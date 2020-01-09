package xyz.luan.audioplayers;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
abstract public class Player implements AudioManager.OnAudioFocusChangeListener, OnAudioInterruptedListener {

    protected static boolean objectEquals(Object o1, Object o2) {
        return o1 == null && o2 == null || o1 != null && o1.equals(o2);
    }

    abstract String getPlayerId();


    abstract void play();

    abstract void stop();

    abstract void release();

    abstract void prepare();

    abstract void pause();

    abstract void setUrl(String url, boolean isLocal);

    abstract void setVolume(double volume);

    abstract void configAttributes(boolean respectSilence, boolean stayAwake, Context context);

    abstract void setReleaseMode(ReleaseMode releaseMode);

    abstract void setOnPreparedListener(MediaPlayer.OnPreparedListener listener);

    abstract int getDuration();

    abstract int getCurrentPosition();

    abstract boolean isActuallyPlaying();

    /**
     * Seek operations cannot be called until after the player is ready.
     */
    abstract void seek(int position);
}

