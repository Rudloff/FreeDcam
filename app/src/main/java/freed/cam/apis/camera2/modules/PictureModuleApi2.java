/*
 *
 *     Copyright (C) 2015 Ingo Fuchs
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * /
 */

package freed.cam.apis.camera2.modules;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Size;
import android.view.Surface;

import com.troop.freedcam.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import freed.cam.apis.basecamera.CameraWrapperInterface;
import freed.cam.apis.basecamera.modules.ModuleHandlerAbstract.CaptureStates;
import freed.cam.apis.basecamera.parameters.modes.ToneMapChooser;
import freed.cam.apis.camera2.Camera2Fragment;
import freed.cam.apis.camera2.CameraHolderApi2.CompareSizesByArea;
import freed.cam.apis.camera2.parameters.ae.AeManagerCamera2;
import freed.settings.SettingKeys;
import freed.settings.SettingsManager;
import freed.utils.Log;


/**
 * Created by troop on 12.12.2014.
 */
@TargetApi(VERSION_CODES.LOLLIPOP)
public class PictureModuleApi2 extends AbstractModuleApi2 implements ImageCaptureHolder.RdyToSaveImg
{
    private final String TAG = PictureModuleApi2.class.getSimpleName();
    private String picFormat;
    private int mImageWidth;
    private int mImageHeight;
    protected ImageReader jpegReader;
    protected ImageReader rawReader;
    protected int imagecount;
    private final int STATE_WAIT_FOR_PRECAPTURE = 0;
    private final int STATE_WAIT_FOR_NONPRECAPTURE = 1;
    private final int STATE_PICTURE_TAKEN = 2;
    private int mState = STATE_PICTURE_TAKEN;
    private long mCaptureTimer;
    private static final long PRECAPTURE_TIMEOUT_MS = 1000;
    private ImageCaptureHolder currentCaptureHolder;
    private final int MAX_IMAGES = 5;
    protected List<File> filesSaved;

    private boolean isBurstCapture = false;
    private int burstCount = 1;

    private boolean captureDng = false;
    private boolean captureJpeg = false;
    protected Camera2Fragment cameraUiWrapper;


    public PictureModuleApi2(CameraWrapperInterface cameraUiWrapper, Handler mBackgroundHandler, Handler mainHandler) {
        super(cameraUiWrapper,mBackgroundHandler,mainHandler);
        this.cameraUiWrapper = (Camera2Fragment)cameraUiWrapper;
        name = cameraUiWrapper.getResString(R.string.module_picture);
        filesSaved = new ArrayList<>();
    }

    @Override
    public String LongName() {
        return "Picture";
    }

    @Override
    public String ShortName() {
        return "Pic";
    }

    @Override
    public void InitModule()
    {
        super.InitModule();
        Log.d(TAG, "InitModule");
        changeCaptureState(CaptureStates.image_capture_stop);
        cameraUiWrapper.getParameterHandler().get(SettingKeys.M_Burst).SetValue(0, true);
        if (cameraUiWrapper.captureSessionHandler.getSurfaceTexture() != null)
            startPreview();
    }

    @Override
    public void DestroyModule()
    {
        Log.d(TAG, "DestroyModule");
        cameraUiWrapper.captureSessionHandler.CloseCaptureSession();
        cameraUiWrapper.getFocusPeakProcessor().kill();
        if (rawReader != null){
            rawReader.close();
            rawReader = null;
        }
        if (jpegReader != null){
            jpegReader.close();
            jpegReader = null;
        }
    }

    @Override
    public void DoWork()
    {
        Log.d(TAG, "startWork: start new progress");
        if(!isWorking)
            mBackgroundHandler.post(TakePicture);
        else if (isWorking)
        {
            Log.d(TAG, "cancel capture");
            cameraUiWrapper.captureSessionHandler.cancelCapture();
            finishCapture();
            changeCaptureState(CaptureStates.image_capture_stop);
        }
    }

    @Override
    public void startPreview() {


        Log.d(TAG, "Start Preview");
        setOutputSizes();

        int sensorOrientation = cameraHolder.characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int orientationToSet = (360 +cameraUiWrapper.getActivityInterface().getOrientation() + sensorOrientation)%360;
        if (SettingsManager.get(SettingKeys.orientationHack).get())
            orientationToSet = (360 +cameraUiWrapper.getActivityInterface().getOrientation() + sensorOrientation+180)%360;
        Log.d(TAG, "orientation to set :" +orientationToSet);
        cameraUiWrapper.captureSessionHandler.SetParameter(CaptureRequest.JPEG_ORIENTATION, orientationToSet);

        // Here, we create a CameraCaptureSession for camera preview

        Size previewSize = cameraUiWrapper.getSizeForPreviewDependingOnImageSize(ImageFormat.YUV_420_888, mImageWidth, mImageHeight);
        if (cameraUiWrapper.getFocusPeakProcessor() != null)
        {
            cameraUiWrapper.getFocusPeakProcessor().kill();
        }


        SurfaceTexture texture = cameraUiWrapper.captureSessionHandler.getSurfaceTexture();
        texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewsurface = new Surface(texture);
        final int w = previewSize.getWidth();
        final int h = previewSize.getHeight();

        if (SettingsManager.get(SettingKeys.EnableRenderScript).get()) {
            int orientation = 0;
            switch (sensorOrientation)
            {
                case 90:
                    orientation = 0;
                    break;
                case 180:
                    orientation =90;
                    break;
                case 270: orientation = 180;
                    break;
                case 0: orientation = 270;
                    break;
            }

            final int or = orientation;
            mainHandler.post(() -> cameraUiWrapper.captureSessionHandler.SetTextureViewSize(w, h,or,or+180,true));
            cameraUiWrapper.getFocusPeakProcessor().Reset(previewSize.getWidth(), previewSize.getHeight());
            cameraUiWrapper.getFocusPeakProcessor().setOutputSurface(previewsurface);
            Surface camerasurface = cameraUiWrapper.getFocusPeakProcessor().getInputSurface();
            cameraUiWrapper.captureSessionHandler.AddSurface(camerasurface, true);
        }
        else
        {
            int orientation = 0;
            switch (sensorOrientation)
            {
                case 90:
                    orientation = 270;
                    break;
                case 180:
                    orientation =0;
                    break;
                case 270: orientation = 90;
                    break;
                case 0: orientation = 180;
                    break;
            }
            final int or = orientation;
            cameraUiWrapper.captureSessionHandler.AddSurface(previewsurface, true);
            mainHandler.post(() -> cameraUiWrapper.captureSessionHandler.SetTextureViewSize(w, h, or,or+180,false));
        }

        if (jpegReader != null)
            cameraUiWrapper.captureSessionHandler.AddSurface(jpegReader.getSurface(),false);
        if (rawReader != null)
            cameraUiWrapper.captureSessionHandler.AddSurface(rawReader.getSurface(),false);

        cameraUiWrapper.captureSessionHandler.CreateCaptureSession();

        cameraUiWrapper.captureSessionHandler.createImageCaptureRequestBuilder();
        if (jpegReader != null)
            cameraUiWrapper.captureSessionHandler.setImageCaptureSurface(jpegReader.getSurface());
        if (rawReader != null)
            cameraUiWrapper.captureSessionHandler.setImageCaptureSurface(rawReader.getSurface());
        if (parameterHandler.get(SettingKeys.M_Burst) != null)
            parameterHandler.get(SettingKeys.M_Burst).fireStringValueChanged(parameterHandler.get(SettingKeys.M_Burst).GetStringValue());
        cameraUiWrapper.firePreviewOpen();
    }

    private void setOutputSizes() {
        String picSize = SettingsManager.get(SettingKeys.PictureSize).get();
        Size largestImageSize = Collections.max(
                Arrays.asList(cameraHolder.map.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());
        picFormat = SettingsManager.get(SettingKeys.PictureFormat).get();
        if (TextUtils.isEmpty(picFormat)) {
            picFormat = SettingsManager.getInstance().getResString(R.string.pictureformat_jpeg);
            SettingsManager.get(SettingKeys.PictureFormat).set(picFormat);
            parameterHandler.get(SettingKeys.PictureFormat).fireStringValueChanged(picFormat);

        }

        String[] split = picSize.split("x");
        if (split.length < 2)
        {
            mImageWidth = largestImageSize.getWidth();
            mImageHeight = largestImageSize.getHeight();
        }
        else
        {
            mImageWidth = Integer.parseInt(split[0]);
            mImageHeight = Integer.parseInt(split[1]);
        }
        //create new ImageReader with the size and format for the image, its needed for p9 else dual or single cam ignores expotime on a dng only capture....
            jpegReader = ImageReader.newInstance(mImageWidth, mImageHeight, ImageFormat.JPEG, MAX_IMAGES);
            Log.d(TAG, "ImageReader JPEG");
        if (picFormat.equals(SettingsManager.getInstance().getResString(R.string.pictureformat_jpeg)))
        {
            captureDng = false;
            captureJpeg = true;
        }

        if (picFormat.equals(SettingsManager.getInstance().getResString(R.string.pictureformat_dng16)) || picFormat.equals(SettingsManager.getInstance().getResString(R.string.pictureformat_jpg_p_dng)))
        {
            Log.d(TAG, "ImageReader RAW_SENSOR");
            largestImageSize = Collections.max(Arrays.asList(cameraHolder.map.getOutputSizes(ImageFormat.RAW_SENSOR)), new CompareSizesByArea());
            rawReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(), ImageFormat.RAW_SENSOR, MAX_IMAGES);
            if(picFormat.equals(SettingsManager.getInstance().getResString(R.string.pictureformat_dng16)))
            {
                captureDng = true;
                captureJpeg = false;
            }
            else
            {
                captureJpeg = true;
                captureDng = true;
            }
        }
        else if (picFormat.equals(SettingsManager.getInstance().getResString(R.string.pictureformat_dng10)))
        {
            Log.d(TAG, "ImageReader RAW10");
            largestImageSize = Collections.max(Arrays.asList(cameraHolder.map.getOutputSizes(ImageFormat.RAW10)), new CompareSizesByArea());
            rawReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(), ImageFormat.RAW10, MAX_IMAGES);
            captureDng = true;
            captureJpeg = false;

        }
        else if (picFormat.equals(SettingsManager.getInstance().getResString(R.string.pictureformat_dng12)))
        {
            Log.d(TAG, "ImageReader RAW12");
            largestImageSize = Collections.max(Arrays.asList(cameraHolder.map.getOutputSizes(ImageFormat.RAW12)), new CompareSizesByArea());
            rawReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(), ImageFormat.RAW12, MAX_IMAGES);
            captureDng = true;
            captureJpeg = false;

        }
        else if (picFormat.equals(SettingsManager.getInstance().getResString(R.string.pictureformat_bayer)))
        {
            Log.d(TAG, "ImageReader RAW12");
            largestImageSize = Collections.max(Arrays.asList(cameraHolder.map.getOutputSizes(ImageFormat.RAW_SENSOR)), new CompareSizesByArea());
            rawReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(), ImageFormat.RAW_SENSOR, MAX_IMAGES);
            captureDng = false;
            captureJpeg = false;

        }
        else {
            if (rawReader != null) {
                rawReader.close();
                rawReader = null;
            }
            rawReader = null;
            captureDng = false;
        }
    }


    @Override
    public void stopPreview()
    {
        DestroyModule();
    }

    private Runnable TakePicture = new Runnable()
    {
        @Override
        public void run() {
            isWorking = true;
            Log.d(TAG, SettingsManager.get(SettingKeys.PictureFormat).get());
            imagecount = 0;
            burstCount = Integer.parseInt(parameterHandler.get(SettingKeys.M_Burst).GetStringValue());
            if (burstCount > 1)
                isBurstCapture = true;
            else
                isBurstCapture =false;
            onStartTakePicture();

            if (SettingsManager.getInstance().hasCamera2Features() && cameraUiWrapper.captureSessionHandler.getPreviewParameter(CaptureRequest.CONTROL_AE_MODE) != CaptureRequest.CONTROL_AE_MODE_OFF) {
                PictureModuleApi2.this.setCaptureState(STATE_WAIT_FOR_PRECAPTURE);
                Log.d(TAG,"Start AE Precapture");
                startTimerLocked();
                cameraUiWrapper.captureSessionHandler.StartAePrecapture(aecallback);
            }
            else
            {
                Log.d(TAG, "captureStillPicture");
                captureStillPicture();
            }

        }

    };

    protected void onStartTakePicture()
    {

    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     *
     */
    protected void captureStillPicture() {

        Log.d(TAG,"########### captureStillPicture ###########");
        currentCaptureHolder = new ImageCaptureHolder(cameraHolder.characteristics, captureDng, captureJpeg, cameraUiWrapper.getActivityInterface(),this,this, this);
        currentCaptureHolder.setFilePath(getFileString(), SettingsManager.getInstance().GetWriteExternal());
        currentCaptureHolder.setForceRawToDng(SettingsManager.get(SettingKeys.forceRawToDng).get());
        currentCaptureHolder.setToneMapProfile(((ToneMapChooser)cameraUiWrapper.getParameterHandler().get(SettingKeys.TONEMAP_SET)).getToneMap());
        currentCaptureHolder.setSupport12bitRaw(SettingsManager.get(SettingKeys.support12bitRaw).get());

        Log.d(TAG, "captureStillPicture ImgCount:"+ imagecount +  " ImageCaptureHolder Path:" + currentCaptureHolder.filepath);

        if (cameraUiWrapper.getParameterHandler().get(SettingKeys.LOCATION_MODE).GetStringValue().equals(SettingsManager.getInstance().getResString(R.string.on_)))
        {
            currentCaptureHolder.setLocation(cameraUiWrapper.getActivityInterface().getLocationManager().getCurrentLocation());
            cameraUiWrapper.captureSessionHandler.SetParameter(CaptureRequest.JPEG_GPS_LOCATION,cameraUiWrapper.getActivityInterface().getLocationManager().getCurrentLocation());
        }

        String cmat = SettingsManager.get(SettingKeys.MATRIX_SET).get();
        if (cmat != null && !TextUtils.isEmpty(cmat) &&!cmat.equals("off")) {
            currentCaptureHolder.setCustomMatrix(SettingsManager.getInstance().getMatrixesMap().get(cmat));
        }
        if (jpegReader != null)
            jpegReader.setOnImageAvailableListener(currentCaptureHolder,mBackgroundHandler);
        if (rawReader != null)
        {
            rawReader.setOnImageAvailableListener(currentCaptureHolder,mBackgroundHandler);
        }

        cameraUiWrapper.captureSessionHandler.StopRepeatingCaptureSession();

        Log.d(TAG, "CancelRepeatingCaptureSessoion set imageRdyCallback");
        cameraUiWrapper.captureSessionHandler.StopRepeatingCaptureSession();
        prepareCaptureBuilder(imagecount);
        changeCaptureState(CaptureStates.image_capture_start);
        Log.d(TAG, "StartStillCapture");
        cameraUiWrapper.captureSessionHandler.StartImageCapture(currentCaptureHolder, mBackgroundHandler);
    }

    protected void prepareCaptureBuilder(int captureNum)
    {

    }

    private String getAeStateString(int ae)
    {
        switch (ae)
        {
            case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                return "CONTROL_AE_STATE_INACTIVE";
            case CaptureResult.CONTROL_AE_STATE_SEARCHING:
                return "CONTROL_AE_STATE_SEARCHING";
            case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                return "CONTROL_AE_STATE_CONVERGED";
            case CaptureResult.CONTROL_AE_STATE_LOCKED:
                return "CONTROL_AE_STATE_LOCKED";
            case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                return "CONTROL_AE_STATE_FLASH_REQUIRED";
            case CaptureResult.CONTROL_AE_STATE_PRECAPTURE:
                return "CONTROL_AE_STATE_PRECAPTURE";
            default:
                return "";
        }
    }

    private String getCaptureState(int state)
    {
        switch (state)
        {
            case STATE_WAIT_FOR_PRECAPTURE:
                return "STATE_WAIT_FOR_PRECAPTURE";
            case STATE_PICTURE_TAKEN:
                return "STATE_PICTURE_TAKEN";
            case STATE_WAIT_FOR_NONPRECAPTURE:
                return "STATE_WAIT_FOR_NONPRECAPTURE";
            default:
                return "";
        }
    }

    /**
     * get used when a continouse focus mode is active to get best focus and exposure for capture.
     * when both are found or timeout, a capture gets started
     * this get called repeating by the camera till a capture happen
     */
    private CaptureCallback aecallback = new CaptureCallback()
    {
        private void processResult(CaptureResult partialResult)
        {
            Integer aeState = partialResult.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState != null)
                Log.e(TAG, "CurrentCaptureState:" + getCaptureState(mState) + " AE_STATE:" + getAeStateString(aeState));
            switch (mState)
            {
                case STATE_WAIT_FOR_PRECAPTURE:
                    Log.d(TAG,"STATE_WAIT_FOR_PRECAPTURE  AESTATE:" + aeState);
                    if (aeState == null)
                    {
                        setCaptureState(STATE_PICTURE_TAKEN);
                        captureStillPicture();
                    }
                    else if (aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
                    {
                        Log.e(TAG,"Wait For nonprecapture");
                        setCaptureState(STATE_WAIT_FOR_NONPRECAPTURE);
                        if (hitTimeoutLocked())
                        {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        }
                    }
                    else if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED)
                    {
                        Log.d(TAG,"CONTROL_AE_STATE_CONVERGED captureStillPicture");
                        setCaptureState(STATE_PICTURE_TAKEN);
                        captureStillPicture();
                    }
                    break;
                case STATE_WAIT_FOR_NONPRECAPTURE:
                    Log.d(TAG,"STATE_WAIT_FOR_NONPRECAPTURE");
                    setCaptureState(STATE_PICTURE_TAKEN);
                    captureStillPicture();
                    break;
            }

        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult partialResult)
        {
            processResult(partialResult);
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            processResult(partialResult);
        }
    };

    private void setCaptureState(int state)
    {
        mState = state;
        Log.d(TAG, "mState:" + getCaptureState(state));
    }

    private void startTimerLocked() {
        mCaptureTimer = SystemClock.elapsedRealtime();
    }

    private boolean hitTimeoutLocked() {
        return (SystemClock.elapsedRealtime() - mCaptureTimer) > PRECAPTURE_TIMEOUT_MS;
    }

    private String getFileString()
    {
        if (Integer.parseInt(parameterHandler.get(SettingKeys.M_Burst).GetStringValue()) > 1)
            return cameraUiWrapper.getActivityInterface().getStorageHandler().getNewFilePath(SettingsManager.getInstance().GetWriteExternal(), "_" + imagecount);
        else
            return cameraUiWrapper.getActivityInterface().getStorageHandler().getNewFilePath(SettingsManager.getInstance().GetWriteExternal(),"");
    }

    /**
     * Reset the capturesession to preview
     *
     */
    protected void finishCapture() {
        isWorking = false;
        changeCaptureState(CaptureStates.image_capture_stop);
        try
        {
            imagecount++;
            Log.d(TAG,"finished Capture:" + imagecount);
            if (Integer.parseInt(parameterHandler.get(SettingKeys.M_Burst).GetStringValue())  > imagecount) {
                captureStillPicture();
            }
            else if (cameraUiWrapper.captureSessionHandler.getPreviewParameter(CaptureRequest.CONTROL_AE_MODE) == CaptureRequest.CONTROL_AE_MODE_OFF &&
                    cameraUiWrapper.captureSessionHandler.getPreviewParameter(CaptureRequest.SENSOR_EXPOSURE_TIME)> AeManagerCamera2.MAX_PREVIEW_EXPOSURETIME) {
                cameraUiWrapper.captureSessionHandler.SetPreviewParameter(CaptureRequest.SENSOR_EXPOSURE_TIME, AeManagerCamera2.MAX_PREVIEW_EXPOSURETIME);
                cameraUiWrapper.captureSessionHandler.SetPreviewParameter(CaptureRequest.SENSOR_FRAME_DURATION, AeManagerCamera2.MAX_PREVIEW_EXPOSURETIME);
                Log.d(TAG, "CancelRepeatingCaptureSessoion set onSessionRdy");
                cameraUiWrapper.captureSessionHandler.CancelRepeatingCaptureSession();
                onSesssionRdy();
            }
            else {
                onSesssionRdy();
            }
        }
        catch (NullPointerException ex) {
            Log.WriteEx(ex);
        }
    }

    private void onSesssionRdy()
    {
        Log.d(TAG, "onSessionRdy() ######################### Rdy to Start Preview, CAPTURE CYCLE DONE #####################");
        cameraUiWrapper.captureSessionHandler.StartRepeatingCaptureSession();
        if (cameraUiWrapper.captureSessionHandler.getPreviewParameter(CaptureRequest.CONTROL_AF_MODE) == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                || cameraUiWrapper.captureSessionHandler.getPreviewParameter(CaptureRequest.CONTROL_AF_MODE) == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
            cameraUiWrapper.captureSessionHandler.SetParameterRepeating(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL,true);
            cameraUiWrapper.captureSessionHandler.SetParameterRepeating(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE,true);
        }
    }


    @Override
    public void internalFireOnWorkDone(File file)
    {
        Log.d(TAG,"internalFireOnWorkDone BurstCount:" +burstCount +" imageCount:" +imagecount);
        if(isBurstCapture && burstCount  >= imagecount) {
            filesSaved.add(file);
            Log.d(TAG,"internalFireOnWorkDone Burst addFile");
        }
        if (isBurstCapture && burstCount  == imagecount)
        {
            Log.d(TAG,"internalFireOnWorkDone Burst done");
            fireOnWorkFinish(filesSaved.toArray(new File[filesSaved.size()]));
            filesSaved.clear();
        }
        else if (!isBurstCapture)
            fireOnWorkFinish(file);
    }

    @Override
    public void fireOnWorkFinish(File[] files) {
        super.fireOnWorkFinish(files);
    }

    @Override
    public void onRdyToSaveImg(ImageCaptureHolder holder) {
        //holder.getRunner().run();

        Log.d(TAG,"onRdyToSaveImg");
        finishCapture();
    }
}
