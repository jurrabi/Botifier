package com.github.grimpy.botifier;

import java.util.ArrayList;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;


class Botification implements Parcelable {
	public String mPackageLabel;
	public String mDescription;
	public String mText;
	public String mPkg;
	public int mId;
	public Notification mNotification;
	private SharedPreferences mSharedPref;
    
	public int mOffset;
	public String mTag;
	public boolean mRead;
	private static String TAG = "Botifier";
	private static final int TIMESTAMPID = 16908388;
	
	
    public static final Parcelable.Creator<Botification> CREATOR = new Parcelable.Creator<Botification>() {
        public Botification createFromParcel(Parcel in) {
        	Notification not = Notification.CREATOR.createFromParcel(in);
        	int id = in.readInt();
        	String pkg = in.readString();
        	String tag = in.readString();
            return new Botification(not, id, pkg, tag);
        }

		@Override
		public Botification[] newArray(int size) {
			return new Botification[size];
		}

    };
    
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		mNotification.writeToParcel(dest, flags);
		dest.writeInt(mId);
		dest.writeString(mPkg);		
		dest.writeString(mTag);
	}
	
	public Botification(Notification notification, int id, String pkg, String tag) {
		mId = id;
		mPkg = pkg;
		mTag = tag;
		mDescription = notification.tickerText.toString();
		mNotification = notification;
		mOffset = 0;
		mRead = false;
	}
	
	private String getPackageLabel(Service service, String packagename){
		PackageManager packageManager = service.getPackageManager();
		ApplicationInfo ai;
		try {
		    ai = packageManager.getApplicationInfo( packagename, 0);
		} catch (final NameNotFoundException e) {
		    ai = null;
		}
		return (String) (ai != null ? packageManager.getApplicationLabel(ai) : packagename);

	}

	
	public void load(Service service) {
		mPackageLabel = getPackageLabel(service, mPkg);
		mText = TextUtils.join("\n",extractTextFromNotification(service, mNotification));
		mSharedPref = PreferenceManager.getDefaultSharedPreferences(service);
	    
	}
	
	public boolean hasNext() {
		int maxlength = Integer.valueOf(mSharedPref.getString("maxlength", "0"));
		if (maxlength == 0) {
			return false;
		}
		return (mOffset+1)*maxlength < mText.length();
	}
	
	public String getPreference(String key) {
		return getPreference(key, false);
	}
	
	public String getPreference(String key, boolean full) {
		String message = mSharedPref.getString(key, "");
		int maxlength = Integer.valueOf(mSharedPref.getString("maxlength", "0"));
		message = message.replace("%f", toString());
		message = message.replace("%a", mPackageLabel);
		message = message.replace("%d", mDescription);
		message = message.replace("%m", mText);
		
		if (!full) {
			if (maxlength != 0 && message.length() > maxlength) {
				int start = mOffset * maxlength;
				int end = start + maxlength;
				if (end >= message.length()) {
					end = message.length() -1;
					mOffset = -1;
				}
				String result = message.substring(start, end);
				mOffset++;
				return result;
			}
		}
		return message;
	}
	
	public String toString() {
		return String.format("%s %s %s", mPackageLabel, mDescription, mText);
	}

	@Override
	public boolean equals(Object o) {
		if (Botification.class.isInstance(o)) {
			Botification not = (Botification) o;
			if (not.mTag == mTag &&
			    not.mPkg == mPkg &&
			    not.mId == mId) {
				return true;
			}
		}
		return false;
	}
	
    private void extractViewType(ArrayList<View> outViews, Class viewtype, View source) {
    	if (ViewGroup.class.isInstance(source)) {
    		ViewGroup vg = (ViewGroup) source;
    		for (int i = 0; i < vg.getChildCount(); i++) {
    			extractViewType(outViews, viewtype, vg.getChildAt(i));
				
			}
    	} else if(viewtype.isInstance(source)) {
			outViews.add(source);
    	}
    }
    
    private ArrayList<String> extractTextFromNotification(Service service, Notification notification) {
    	ArrayList<String> result = null;
	    result =  extractTextFromNotification(service, notification.bigContentView);
	    if (result == null) {
	    	result = extractTextFromNotification(service, notification.contentView);
	    }
	    return result;

    }

    
    private ArrayList<String> extractTextFromNotification(Service service, RemoteViews view) {
    	LayoutInflater inflater = (LayoutInflater) service.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	    ArrayList<String> result = new ArrayList<String>();
	    if (view == null) {
	    	Log.d(TAG, "View is empty");
	    	return null;
	    }
		try {
			int layoutid = view.getLayoutId();
			ViewGroup localView = (ViewGroup) inflater.inflate(layoutid, null);
		    view.reapply(service.getApplicationContext(), localView);
		    ArrayList<View> outViews = new ArrayList<View>();
		    extractViewType(outViews, TextView.class, localView);
		    for (View  ttv: outViews) {
		    	TextView tv = (TextView) ttv;
		    	String txt = tv.getText().toString();
		    	if (!TextUtils.isEmpty(txt) && tv.getId() != TIMESTAMPID) {
		    		result.add(txt);
		    	}
			}
		} catch (Exception e) {
			Log.d(TAG, "FAILED to load notification " + e.toString());
			Log.wtf(TAG, e);
			return null;
			//notification might have dissapeared by now
		}
		Log.d(TAG, "Return result" + result);
	    return result;
    }

	@Override
	public int describeContents() {
		return 0;
	}
}
