package com.csipsimple.newui;

import junit.framework.Test;

import com.csipsimple.R;
import com.csipsimple.api.ISipService;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.db.DBProvider;
import com.csipsimple.ui.SipHome;
import com.csipsimple.utils.PreferencesProviderWrapper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class HomeActivity extends Activity implements OnClickListener {
	
	private ImageView imageViewSetting;
	private LinearLayout btnRemoteOpenDoor;
	
	
	private ISipService service;
	private ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
			service = ISipService.Stub.asInterface(arg1);
			/*
			 * timings.addSplit("Service connected"); if(configurationService !=
			 * null) { timings.dumpToLog(); }
			 */
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			service = null;
		}
	};
	
	boolean tvTest = true;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		
		Intent serviceIntent = new Intent(SipManager.INTENT_SIP_SERVICE);
		this.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
		
		imageViewSetting = (ImageView) findViewById(R.id.iv_homeactivity_setting);
		imageViewSetting.setOnClickListener(this);
		btnRemoteOpenDoor = (LinearLayout) findViewById(R.id.btn_remote_open_door);
		btnRemoteOpenDoor.setOnClickListener(this);
		
		
	}
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		if (v == imageViewSetting) {
			Intent  i= new Intent(getApplicationContext(),HomeSettingActivity.class);
			startActivity(i);
		}else  if(v == btnRemoteOpenDoor) {
			if (tvTest) {
				Intent intent = new Intent(this,SipHome.class);
				startActivity(intent);
			}
			
			if (obtainSipServiceSetupState(getApplicationContext(), PreferencesProviderWrapper.HAS_ALREADY_SETUP_SERVICE)) {
				sendMessage();
			}else {
				Toast.makeText(getApplicationContext(), "服务未注册", Toast.LENGTH_LONG).show();
			}
		}
	}

	
	   private void sendMessage() {
	        if (service != null) {
	            SipProfile acc = SipProfile.getProfileFromDbId(this, 1,DBProvider.ACCOUNT_FULL_PROJECTION);
	            if (acc != null && acc.id != SipProfile.INVALID_ID) {
	                try {
	                    String textToSend = "opendoor";
	                    if(!TextUtils.isEmpty(textToSend)) {
	                        service.sendMessage(textToSend, "001", (int) acc.id);
	                        Toast.makeText(getApplicationContext(), "成功开锁", Toast.LENGTH_SHORT).show();
	                    }
	                } catch (RemoteException e) {
	                }
	            }
	        }
	    }
	public boolean obtainSipServiceSetupState(Context context,String key){
	    PreferencesProviderWrapper wrapper = new PreferencesProviderWrapper(context);    
	    return wrapper.getPreferenceBooleanValue(key, false);
	}
	
}
