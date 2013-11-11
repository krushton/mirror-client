package com.krushton.mirror;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class FullscreenActivity extends Activity {

	private Camera mCamera;
    private CameraPreview mPreview;
    private final static String TAG = "FULLSCREEN ACTIVITY";
    public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
    private Bitmap bmp;
    private Boolean foundFaceRecently = false;
    
    TimerTask scanTask;
	final Handler handler = new Handler();
	Timer t = new Timer();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//hide the action bar
	    getActionBar().hide();                                   
	    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
	    getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

	    setContentView(R.layout.activity_fullscreen);
	    
	    mCamera = openFrontFacingCamera();
		
		//the following setting is required for nexus 4
		mCamera.setDisplayOrientation(90);
      
		// Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);
        mCamera.startPreview();
        
        
		int faceNum = mCamera.getParameters().getMaxNumDetectedFaces();
		
      /*  if (faceNum > 0) {
        	//Face detect supported by the hardware
			mCamera.setFaceDetectionListener(new Camera.FaceDetectionListener() {
	            @Override public void onFaceDetection(android.hardware.Camera.Face[] faces, Camera camera) {
	                if(faces.length > 0) {
	                    Log.d(TAG, "Found face");
	                    startPictureCountdown();
	                }

	            }
	        });
			mCamera.startFaceDetection();
		} else { */
			//Face detect not supported in hardware, must use software version
			Log.d(TAG, "Face rec not supported");
			scan();
		//}

    	
	}
	
	//every 1 second, scan the saved frame looking for faces. 
	public void scan(){

		scanTask = new TimerTask() {
	        public void run() {
	                handler.post(new Runnable() {
	                        public void run() {
	                        	
	                        	if (!foundFaceRecently) {
	                        	 
	                        		mCamera.setOneShotPreviewCallback(new PreviewCallback(){

	    								@Override
	    								public void onPreviewFrame(byte[] data, Camera camera) {
	    									
	    									Camera.Parameters parameters = camera.getParameters();
	    									int format = parameters.getPreviewFormat();

	    									// YUV formats require more conversion in order to be scannable by the face finder
	    									if (format == ImageFormat.NV21 /*|| format == ImageFormat.YUY2 || format == ImageFormat.NV16*/)
	    									{
	    								    	int w = parameters.getPreviewSize().width;
	    								    	int h = parameters.getPreviewSize().height;
	    								    	
	    								    	// Get the YuV image
	    								    	YuvImage yuv_image = new YuvImage(data, format, w, h, null);
	    								    	
	    								    	// Convert YuV to Jpeg
	    										Rect rect = new Rect(0, 0, w, h);
	    										ByteArrayOutputStream output_stream = new ByteArrayOutputStream();
	    										yuv_image.compressToJpeg(rect, 100, output_stream);
	    										
	    										// Convert from Jpeg to Bitmap
	    										BitmapFactory.Options op = new BitmapFactory.Options();
	    										op.inPreferredConfig = Bitmap.Config.RGB_565;
	    										
	    										bmp = BitmapFactory.decodeByteArray(output_stream.toByteArray(), 0, output_stream.size(),op);
	    										
	    										
	    										Matrix matrix = new Matrix();
	    										//setting for nexus 4 and samsung tab
	    									    matrix.postRotate(-90);
	    									    
	    										//matrix.postRotate(0);
	    									    //rotate the image
	    									    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
	    									    
	    									} else {
	    										bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
	    									}
	    									
	    									if (bmp != null) {
	    										checkForFaces();
	    									}

	    								}
	    	                         });
	    	                         
	                        	}
	                         
	                        }
	               });
	        }};

	
		    t.schedule(scanTask, 300, 10000); 

	 }

     //stop scanning
	 public void stopScan(){

		   if(scanTask!=null){
		      Log.d("TIMER", "timer canceled");
		      t.cancel();
		 }

	}
    
    //get a reference to the front-facing camera
	private Camera openFrontFacingCamera() {
	    int cameraCount = 0;
	    Camera cam = null;
	    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
	    cameraCount = Camera.getNumberOfCameras();
	    for ( int camIdx = 0; camIdx < cameraCount; camIdx++ ) {
	        Camera.getCameraInfo( camIdx, cameraInfo );
	        if ( cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT  ) {
	            try {
	                cam = Camera.open( camIdx );
	            } catch (RuntimeException e) {
	            	
	                Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
	            }
	        }
	    }

	    return cam;
	}
	
	//when a picture is taken, save it and then post to remote server
	private PictureCallback mPicture = new PictureCallback() {

	    @Override
	    public void onPictureTaken(byte[] data, Camera camera) {
	    	
	    	File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
	        if (pictureFile == null){
	            Log.d(TAG, "Error creating media file, check storage permissions: ");
	            return;
	        }
	        
	        try {
	            FileOutputStream fos = new FileOutputStream(pictureFile);
	            fos.write(data);
	            fos.close();
	        } catch (FileNotFoundException e) {
	            Log.d(TAG, "File not found: " + e.getMessage());
	        } catch (IOException e) {
	            Log.d(TAG, "Error accessing file: " + e.getMessage());
	        }
	     /*  
	        Drawable d = new BitmapDrawable(getResources(), bmp);
	        mPreview.setBackground(d);
	    */	
	    	PostTask task = new PostTask();
	    	task.execute(pictureFile.getAbsolutePath());
	    }
	};
	
	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type){
	    // To be safe, you should check that the SDCard is mounted
	    // using Environment.getExternalStorageState() before doing this.

	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_PICTURES), "MyCameraApp");
	    /* This location works best if you want the created images to be shared
	    / between applications and persist after your app has been uninstalled. */

	    // Create the storage directory if it does not exist
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	            Log.d("MyCameraApp", "failed to create directory");
	            return null;
	        }
	    }

	    /* Create a media file name */
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "IMG_"+ timeStamp + ".jpg");
	    } else if(type == MEDIA_TYPE_VIDEO) {
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "VID_"+ timeStamp + ".mp4");
	    } else {
	        return null;
	    }

	    return mediaFile;
	}
	
	/* scan the bitmap in bmp for faces using FaceDetector */
	public void checkForFaces() {
	    // Ask for 1 face
	    Face faces[] = new FaceDetector.Face[1];
	    FaceDetector detector = new FaceDetector( bmp.getWidth(), bmp.getHeight(), 1 );
	    int count = detector.findFaces( bmp, faces );

	    if( count > 0 ) {
	    	Log.d(TAG, "FOUND FACE");
	    	foundFaceRecently = true;
	    	mCamera.takePicture(null, null, mPicture);
	    	mCamera.startPreview();
	    } else {
	    	Log.d(TAG, "NO FACE");
	    }
	    
	}
		
	/* sends images to the OCR API library in the background */
	public class PostTask extends AsyncTask<String, Void, String> {

		@Override
		protected String doInBackground(String... args) {
				
                String inputFile = args[0];
                File f = new File(inputFile);
               
                Bitmap btmp = BitmapFactory.decodeFile(inputFile);
                
                Matrix matrix = new Matrix();
                //for nexus 4 and samsung tablet
				//have to rotate it again for some reason.
			    matrix.postRotate(-90);
			    
			    //rotate the image
				Bitmap btmpCopy = Bitmap.createBitmap(btmp, 0, 0, btmp.getWidth(), btmp.getHeight(), matrix, true);
			    
				//convert to grayscale for that cctv feel
				Bitmap gray = convertBitmapToGrayscale(btmpCopy);			
                ByteArrayOutputStream bao = new ByteArrayOutputStream();
                gray.compress(CompressFormat.JPEG, 100, bao);
                
                //attach image to post task
                ByteArrayBody body = new ByteArrayBody(bao.toByteArray(), "image/jpeg", "image");
                
                String shortName = f.getName();        
                String responseString ="";

                try {
                      
                        String url = "http://mirrorsapp.herokuapp.com/postfiles";
                        HttpClient httpclient = new DefaultHttpClient();
                        HttpPost httpPost = new HttpPost(url);

                        try {
                            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
                            entity.addPart("image", body);
                            entity.addPart("filename", new StringBody(shortName));
                            
                            String code = getResources().getString(R.string.code);
                            entity.addPart("code", new StringBody(code));
                            
                            httpPost.setEntity(entity);

                            HttpResponse response = httpclient.execute(httpPost);
                          
	                          if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
	                              ByteArrayOutputStream out = new ByteArrayOutputStream();
	                              response.getEntity().writeTo(out);
	                              out.close();
	                              responseString = out.toString();
	
	                          } else{
	                              //Closes the connection.
	                              response.getEntity().getContent().close();
	                              throw new IOException(response.getStatusLine().getReasonPhrase());
	                          }
                         
                        } catch (Exception e) {
                        	Log.d(TAG, e.getMessage());
                        } 
                        
                } catch (Exception e) {
                		Log.d(TAG, "ERROR: " + e.getCause());
                        Log.d(TAG, "Error: " + e.getMessage());
                }
                return responseString;
        }
		
		@Override
		protected void onPostExecute(String result) {
			Log.d(TAG,result);
			mCamera.startPreview();
		}
	
	}
	
	private Bitmap convertBitmapToGrayscale(Bitmap bmpOriginal)
	{        
	    int width, height;
	    height = bmpOriginal.getHeight();
	    width = bmpOriginal.getWidth();    

	    Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
	    Canvas c = new Canvas(bmpGrayscale);
	    Paint paint = new Paint();
	    ColorMatrix cm = new ColorMatrix();
	    cm.setSaturation(0);
	    ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
	    paint.setColorFilter(f);
	    c.drawBitmap(bmpOriginal, 0, 0, paint);
	    return bmpGrayscale;
	}
	}
	
