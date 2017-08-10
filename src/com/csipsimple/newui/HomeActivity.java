package com.csipsimple.newui;

import org.webrtc.videoengine.ViERenderer;

import com.csipsimple.R;
import com.csipsimple.api.ISipService;
import com.csipsimple.api.SipCallSession;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.api.SipCallSession.StatusCode;
import com.csipsimple.db.DBProvider;
import com.csipsimple.service.SipService;
import com.csipsimple.ui.SipHome;
import com.csipsimple.ui.incall.IOnCallActionTrigger;
import com.csipsimple.ui.incall.InCallMediaControl;
import com.csipsimple.utils.PreferencesProviderWrapper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.PowerManager.WakeLock;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class HomeActivity extends Activity implements OnClickListener,IOnCallActionTrigger {

	private ImageView imageViewSetting;
	private LinearLayout btnRemoteOpenDoor;
	private LinearLayout btnTakeCall;
	private LinearLayout btnHangUp;

	private ViewGroup mainFrame;

	private SurfaceView cameraPreview;
	private SurfaceView renderView;

	private Object callMutex = new Object();
	private SipCallSession[] callsInfo = null;
	
	// Screen wake lock for video
	private WakeLock videoWakeLock;
	
	private boolean serviceConnected = false;
	private ISipService service;
	private ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
			service = ISipService.Stub.asInterface(arg1);
			/*
			 * timings.addSplit("Service connected"); if(configurationService !=
			 * null) { timings.dumpToLog(); }
			 */
			try {
				callsInfo = service.getCalls();
				serviceConnected = true;
				runOnUiThread(new UpdateUIFromCallRunnable());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			service = null;
			serviceConnected = false;
			callsInfo = null;
		}
	};

	boolean tvTest = false;

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
		btnTakeCall = (LinearLayout) findViewById(R.id.btn_take_call);
		btnTakeCall.setOnClickListener(this);
		btnHangUp = (LinearLayout) findViewById(R.id.btn_hang_up);
		btnHangUp.setOnClickListener(this);
		mainFrame = (ViewGroup) findViewById(R.id.mainFram);
		registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_CALL_CHANGED));
		
//		proximityManager = new CallProximityManager(this, this, lockOverlay);
		attachVideoPreview();

	}
	private void attachVideoPreview() {
		// Video stuff
		if (cameraPreview == null) {
			cameraPreview = ViERenderer.CreateLocalRenderer(this);
			renderView = ViERenderer.CreateRenderer(this, true);
			mainFrame.addView(renderView,0,new RelativeLayout.LayoutParams(200, 200));
			mainFrame.addView(cameraPreview,0,new RelativeLayout.LayoutParams(200, 200));
		} else {
		}
	}
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		if (v == imageViewSetting) {
			Intent i = new Intent(getApplicationContext(),
					HomeSettingActivity.class);
			startActivity(i);
		} else if (v == btnRemoteOpenDoor) {
			if (tvTest) {
				Intent intent = new Intent(this, SipHome.class);
				startActivity(intent);
			}

			if (obtainSipServiceSetupState(getApplicationContext(),
					PreferencesProviderWrapper.HAS_ALREADY_SETUP_SERVICE)) {
				sendMessage();
			} else {
				Toast.makeText(getApplicationContext(), "服务未注册",
						Toast.LENGTH_LONG).show();
			}
		}else if (v == btnTakeCall) {
			onTrigger(TAKE_CALL, getActiveCallInfo());
		}else if (v == btnHangUp) {
			onTrigger(TERMINATE_CALL, getActiveCallInfo());
		}
	}
	
	private BroadcastReceiver callStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (action.equals(SipManager.ACTION_SIP_CALL_CHANGED)) {
				if (service != null) {
					try {
						synchronized (callMutex) {
							callsInfo = service.getCalls();
							runOnUiThread(new UpdateUIFromCallRunnable());
						}
					} catch (RemoteException e) {
					}
				}
			} 
			
		}
	};

	private void sendMessage() {
		if (service != null) {
			SipProfile acc = SipProfile.getProfileFromDbId(this, 1,
					DBProvider.ACCOUNT_FULL_PROJECTION);
			if (acc != null && acc.id != SipProfile.INVALID_ID) {
				try {
					String textToSend = "opendoor";
					if (!TextUtils.isEmpty(textToSend)) {
						service.sendMessage(textToSend, "001", (int) acc.id);
						Toast.makeText(getApplicationContext(), "成功开锁",
								Toast.LENGTH_SHORT).show();
					}
				} catch (RemoteException e) {
				}
			}
		}
	}

	public boolean obtainSipServiceSetupState(Context context, String key) {
		PreferencesProviderWrapper wrapper = new PreferencesProviderWrapper(
				context);
		return wrapper.getPreferenceBooleanValue(key, false);
	}
	
	public SipCallSession getActiveCallInfo() {
		SipCallSession currentCallInfo = null;
		if (callsInfo == null) {
			return null;
		}
		synchronized (callMutex) {
			for (SipCallSession callInfo : callsInfo) {
				currentCallInfo = getPrioritaryCall(callInfo, currentCallInfo);
			}
		}
		return currentCallInfo;
	}
	
	private SipCallSession getPrioritaryCall(SipCallSession call1,
			SipCallSession call2) {
		// We prefer the not null
		if (call1 == null) {
			return call2;
		} else if (call2 == null) {
			return call1;
		}
		// We prefer the one not terminated
		if (call1.isAfterEnded()) {
			return call2;
		} else if (call2.isAfterEnded()) {
			return call1;
		}
		// We prefer the one not held
		if (call1.isLocalHeld()) {
			return call2;
		} else if (call2.isLocalHeld()) {
			return call1;
		}
		// We prefer the older call
		// to keep consistancy on what will be replied if new call arrives
		return (call1.getCallStart() > call2.getCallStart()) ? call2 : call1;
	}

	private class UpdateUIFromCallRunnable implements Runnable {

		@Override
		public void run() {
			// Current call is the call emphasis by the UI.
			SipCallSession mainCallInfo = getActiveCallInfo();

			if (mainCallInfo != null) {
				
				int state = mainCallInfo.getCallState();

				// int backgroundResId =
				// R.drawable.bg_in_call_gradient_unidentified;

				// We manage wake lock
				switch (state) {
				case SipCallSession.InvState.INCOMING:
					
					break;
				case SipCallSession.InvState.EARLY:
					
					break;
				case SipCallSession.InvState.CALLING:
					
					break;
				case SipCallSession.InvState.CONNECTING:
				
					onTrigger(START_VIDEO, mainCallInfo);
//					onTrigger(TAKE_CALL, mainCallInfo);
					onDisplayVideo(true);
					break;
				case SipCallSession.InvState.CONFIRMED:
					break;
				case SipCallSession.InvState.NULL:
				case SipCallSession.InvState.DISCONNECTED:
					finish();
				
					// This will release locks
					// onDisplayVideo(false);
					// delayedQuit();
					return;

				}

			
			}

		}

	}


	@Override
	public void onTrigger(int whichAction, SipCallSession call) {
		// Sanity check for actions requiring valid call id
		if (whichAction == TAKE_CALL || whichAction == REJECT_CALL
				|| whichAction == DONT_TAKE_CALL
				|| whichAction == TERMINATE_CALL
				|| whichAction == DETAILED_DISPLAY
				|| whichAction == TOGGLE_HOLD || whichAction == START_RECORDING
				|| whichAction == STOP_RECORDING || whichAction == DTMF_DISPLAY
				|| whichAction == XFER_CALL || whichAction == TRANSFER_CALL
				|| whichAction == START_VIDEO || whichAction == STOP_VIDEO) {
			// We check that current call is valid for any actions
			if (call == null) {
				return;
			}
			if (call.getCallId() == SipCallSession.INVALID_CALL_ID) {
				return;
			}
		}

		// Reset proximity sensor timer
		// proximityManager.restartTimer();

		try {
			switch (whichAction) {
			case TAKE_CALL: {
				if (service != null) {

					boolean shouldHoldOthers = false;
					// Well actually we should be always before confirmed
					if (call.isBeforeConfirmed()) {
						shouldHoldOthers = true;
					}
					service.answer(call.getCallId(),SipCallSession.StatusCode.OK);
					// if it's a ringing call, we assume that user wants to
					// hold other calls
					if (shouldHoldOthers && callsInfo != null) {
						for (SipCallSession callInfo : callsInfo) {
							// For each active and running call
							if (SipCallSession.InvState.CONFIRMED == callInfo
									.getCallState()
									&& !callInfo.isLocalHeld()
									&& callInfo.getCallId() != call.getCallId()) {
								service.hold(callInfo.getCallId());

							}
						}
					}
				}
				break;
			}
			case DONT_TAKE_CALL: {
				if (service != null) {
					service.hangup(call.getCallId(), StatusCode.BUSY_HERE);
				}
				break;
			}
			case REJECT_CALL:
			case TERMINATE_CALL: {
				if (service != null) {
					service.hangup(call.getCallId(), 0);
				}
				break;
			}
			case MUTE_ON:
			case MUTE_OFF: {
				if (service != null) {
					service.setMicrophoneMute((whichAction == MUTE_ON) ? true
							: false);
				}
				break;
			}
			case SPEAKER_ON:
			case SPEAKER_OFF: {
				if (service != null) {
					// useAutoDetectSpeaker = false;
					service.setSpeakerphoneOn((whichAction == SPEAKER_ON) ? true
							: false);
				}
				break;
			}
			case DTMF_DISPLAY: {
				// showDialpad(call.getCallId());
				break;
			}
			case TOGGLE_HOLD: {
				if (service != null) {
					// Log.d(THIS_FILE,
					// "Current state is : "+callInfo.getCallState().name()+" / "+callInfo.getMediaStatus().name());
					if (call.getMediaStatus() == SipCallSession.MediaState.LOCAL_HOLD
							|| call.getMediaStatus() == SipCallSession.MediaState.NONE) {
						service.reinvite(call.getCallId(), true);
					} else {
						service.hold(call.getCallId());
					}
				}
				break;
			}
			case MEDIA_SETTINGS: {
				startActivity(new Intent(this, InCallMediaControl.class));
				break;
			}
			case START_VIDEO:
			case STOP_VIDEO: {
				if (service != null) {
					Bundle opts = new Bundle();
					opts.putBoolean(SipCallSession.OPT_CALL_VIDEO,
							whichAction == START_VIDEO);
					service.updateCallOptions(call.getCallId(), opts);
				}
				break;
			}
			case ZRTP_TRUST: {
				if (service != null) {
					service.zrtpSASVerified(call.getCallId());
				}
				break;
			}
			case ZRTP_REVOKE: {
				if (service != null) {
					service.zrtpSASRevoke(call.getCallId());
				}
				break;
			}
			}
		} catch (RemoteException e) {
		}

	}

	@Override
	public void onDisplayVideo(boolean show) {
		runOnUiThread(new UpdateVideoPreviewRunnable(show));
		
	}



    private class UpdateVideoPreviewRunnable implements Runnable {
        private final boolean show;
        UpdateVideoPreviewRunnable(boolean show){
            this.show = show;
        }
        @Override
        public void run() {
            // Update the camera preview visibility 
            if(cameraPreview != null && renderView != null) {
                cameraPreview.setVisibility(show ? View.VISIBLE : View.GONE);
                renderView.setVisibility(show ? View.VISIBLE : View.GONE);
                if(show) {
                    if(videoWakeLock != null) {
                        videoWakeLock.acquire();
                    }
                    SipService.setVideoWindow(SipCallSession.INVALID_CALL_ID, cameraPreview, true);
                    SipService.setVideoWindow(SipCallSession.INVALID_CALL_ID, renderView, false);
                }else {
                    if(videoWakeLock != null && videoWakeLock.isHeld()) {
                        videoWakeLock.release();
                    }
                    SipService.setVideoWindow(SipCallSession.INVALID_CALL_ID, null, true);
                    SipService.setVideoWindow(SipCallSession.INVALID_CALL_ID, null, false);
                }
            }else {
            }
        }
    }
    

}
