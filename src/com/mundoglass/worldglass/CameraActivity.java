package com.mundoglass.worldglass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.rtsp.RtspClient;
import net.majorkernelpanic.streaming.video.VideoQuality;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

/**
 * @author ander.martinez@mundoglass.es based on https://github.com/fyhertz/libstreaming
 * @see www.mundoglass.es
 */
public class CameraActivity extends Activity implements AsyncTaskCompleteInterface<Integer> {

	public final static String TAG = "CameraActivity";
	

	private final static VideoQuality QUALITY_GLASS = new VideoQuality(640, 360, 24, 384000); //wifi
//	private final static VideoQuality QUALITY_GLASS = new VideoQuality(352, 288, 60, 768000); //movil
	//String url = "rtsp://192.168.2.14:1935/live/test.sdp";
	String uri = "";

	
	private VideoQuality mQuality = QUALITY_GLASS;			
	private GestureDetector mGestureDetector;
	
	private RelativeLayout mRelativeLayout; 
	private SurfaceView mSurfaceView;
	private Session mSession;
	private PowerManager.WakeLock mWakeLock;
	private RtspClient mClient;
	private Boolean recording = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		final CameraActivity activity = this;
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		uri = activity.getIntent().getStringExtra("uri");
		
		// Getting layout
		mRelativeLayout = (RelativeLayout) findViewById(R.id.camera_activity);
		// Create gesture detector
		mGestureDetector = createGestureDetector(this);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		mSurfaceView = (SurfaceView) findViewById(R.id.surface);		

		// Configures the SessionBuilder
		SessionBuilder sBuilder = SessionBuilder.getInstance()
		.setContext(getApplicationContext())
		.setSurfaceHolder(mSurfaceView.getHolder())
		.setContext(getApplicationContext())
		.setVideoQuality(QUALITY_GLASS)
		.setAudioEncoder(SessionBuilder.AUDIO_AAC)
		.setVideoEncoder(SessionBuilder.VIDEO_H264);	

		// Configures the RTSP client
		mClient = new RtspClient();

		// Creates the Session
		try {
			mSession = sBuilder.build();
			mClient.setSession(mSession);
		} catch (Exception e) {
			logError(e.getMessage());
			e.printStackTrace();
		}

		// Prevents the phone from going to sleep mode
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK,"net.majorkernelpanic.example3.wakelock");

		mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {

			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				if (mSession != null) {
					try {
						if (mSession.getVideoTrack() != null) {
							mSession.getVideoTrack().setVideoQuality(mQuality);
							
							// Start streaming
							new ToggleStreamAsyncTask(activity).execute();

						}
					} catch (RuntimeException e) {
						logError(e.getMessage());
						e.printStackTrace();
					}
				}
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {
				Log.i(TAG, "surfaceChanged()");
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				Log.i(TAG, "surfaceDestroyed()");
			}

		});		
		
	}


	@Override
	public void onStart() {
		super.onStart();
		// Lock screen
		mWakeLock.acquire();		
	}
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	public void onStop() {
		Log.i(TAG, "onStop()");		
		// Unlock screen
		if (mWakeLock.isHeld()) mWakeLock.release();
			// Setting recording state to disabled
			recording = false;
			mSession.flush();
			
			// Stops the stream and disconnects from the RTSP server
			mClient.stopStream();			
		
			setResult(MainActivity.RESULT_OK);
			super.onStop();
	}	
	
	@Override
	protected void onPause() {

		//Stops the stream and disconnects from the RTSP server
		mClient.stopStream();
		
		// Unlock screen
		if (mWakeLock.isHeld()) mWakeLock.release();
		// Setting recording state to disabled
		recording = false;

		mSession.flush();

		setResult(MainActivity.RESULT_OK);
		super.onPause();
	}


	// Connects/disconnects to the RTSP server and starts/stops the stream
	private class ToggleStreamAsyncTask extends AsyncTask<Void,Void,Integer> {

		public final static int START_SUCCEEDED = 0x00;
		public final static int START_FAILED = 0x01;
		public final static int STOP = 0x02;
		
		private AsyncTaskCompleteInterface callback;
		
		public ToggleStreamAsyncTask(AsyncTaskCompleteInterface callback) {
			this.callback = callback;
		}

		@Override
		protected Integer doInBackground(Void... params) {
			if (!mClient.isStreaming()) {
				String ip,path,userinfo, width, height, bitrate, framerate;
				int port;
				try {
					// We parse the URI written in the Editext
					Uri u = Uri.parse(uri);
					//Pattern p = Pattern.compile("rtsp://(.+):(\\d+)/(.+)");
					//Matcher m = p.matcher(uri); m.find();

					ip = u.getHost();
					port = u.getPort();
					path = u.getPath();
					userinfo = u.getUserInfo();
					width = u.getQueryParameter("w");
					height = u.getQueryParameter("h");
					bitrate = u.getQueryParameter("b");
					framerate = u.getQueryParameter("f");
					
					if (framerate == null) {
						framerate = "24";
					}
					
					if (mSession.getVideoTrack() != null) {
						if (width != null && height != null && bitrate != null) {
							VideoQuality vq = new VideoQuality(Integer.parseInt(width), Integer.parseInt(height), Integer.parseInt(framerate), Integer.parseInt(bitrate));
							mSession.getVideoTrack().setVideoQuality(vq);
						} else {
							mSession.getVideoTrack().setVideoQuality(mQuality);
						}
					}
					
					if (userinfo != null) {
						String tmp[] = userinfo.split(":");
						if (tmp.length == 1) {
							mClient.setCredentials(tmp[0], "");
						} else if (tmp.length == 2) {
							mClient.setCredentials(tmp[0], tmp[1]);
						}
					}
					mClient.setServerAddress(ip, port);
					mClient.setStreamPath("/"+path);
					mClient.startStream(1);
					
					// Init recording flag
					recording = true;
					
					return START_SUCCEEDED;
				} catch (Exception e) {
					//Log.e(TAG, "Error starting streaiming.", e);
					logError(e.getLocalizedMessage());
					return START_FAILED;
				}
			} else {
				// Stops the stream and disconnects from the RTSP server
				mClient.stopStream();				
				// Setting recording state to disabled
				recording = false;
				Log.i(TAG, "*** Recording stopStream()");
				finish();
			}
			return STOP;
		}
		
		protected void onPostExecute(Integer result) {
			callback.onTaskComplete(result);
		}

	}
	
	// Disconnects from the RTSP server and stops the stream
	private class StopStreamAsyncTask extends AsyncTask<Void,Void,Void> {
		@Override
		protected Void doInBackground(Void... params) {
				mClient.stopStream();
				return null;
		}
	}
	


	private void logError(String msg) {
		final String error = (msg == null) ? "Error unknown" : msg; 
		Log.e(TAG,error);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(CameraActivity.this, error, Toast.LENGTH_SHORT).show();	
			}
		});
	}

	
	private GestureDetector createGestureDetector(Context context) {
		final CameraActivity activity = this;
	    GestureDetector gestureDetector = new GestureDetector(context);
	        //Create a base listener for generic gestures
	        gestureDetector.setBaseListener( new GestureDetector.BaseListener() {
	            @Override
	            public boolean onGesture(Gesture gesture) {
		                if (gesture == Gesture.TAP) {		                	
		                    Log.i(TAG, "onGesture TAP");
		                    mRelativeLayout.playSoundEffect(SoundEffectConstants.CLICK);		                    
			       			if (recording == false)  {
				    			 new ToggleStreamAsyncTask(activity).execute();
				    			 //Setting recording state to enabled
				    			 recording = true;
				    			 Log.i(TAG, "*** onSingleTapUp onClick .start");
				    		} else {
				    			 new ToggleStreamAsyncTask(activity).execute();
				    			 //Setting recording state to disable
				    			 recording = false;
				    			 Log.i(TAG, "*** onSingleTapUp onClick .stop");
				    			 
				    			 finish();
				    		}  			        			 
			       			return true;
	                } else if (gesture == Gesture.TWO_TAP) {
	                    Log.i(TAG, "onGesture TWO TAP");
	                	return true;
	                } else if (gesture == Gesture.SWIPE_RIGHT) {
	                	Log.i(TAG, "onGesture SWIPE RIGHT");
	                    return true;
	                } else if (gesture == Gesture.SWIPE_LEFT) {
	                	Log.i(TAG, "onGesture SWIPE LEFT");
	                	return true;
	                } else if (gesture == Gesture.SWIPE_DOWN) {
	                	Log.i(TAG, "onGesture SWIPE DOWN ");
	                	return true;	
	                }
	                
	                return false;
	            }
	        });
	        gestureDetector.setFingerListener(new GestureDetector.FingerListener() {
	            @Override
	            public void onFingerCountChanged(int previousCount, int currentCount) {
	              // do something on finger count changes
	            }
	        });
	        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
	            @Override
	            public boolean onScroll(float displacement, float delta, float velocity) {
	            	Log.i(TAG, "onScroll");
	            	return true;
	            }
	        });
	        return gestureDetector;
	    }

	    /*
	     * Send generic motion events to the gesture detector
	     */
	    @Override
	    public boolean onGenericMotionEvent(MotionEvent event) {
	        if (mGestureDetector != null) {
	            return mGestureDetector.onMotionEvent(event);
	        }
	        return false;
	    }


		@Override
		public void onTaskComplete(Integer result) {
			// TODO Auto-generated method stub
			if (result != ToggleStreamAsyncTask.START_SUCCEEDED) {
				finish();
			}
		}
	
}
