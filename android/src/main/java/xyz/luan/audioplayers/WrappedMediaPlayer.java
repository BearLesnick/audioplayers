package xyz.luan.audioplayers;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;

public class WrappedMediaPlayer extends Player implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private String playerId;

    private String url;
    private double volume = 1.0;
    private boolean respectSilence;
    private boolean stayAwake;
    private MediaPlayer.OnErrorListener errorListener;
    private ReleaseMode releaseMode = ReleaseMode.RELEASE;

    private boolean released = true;
    private boolean prepared = false;
    private boolean playing = false;

    private int shouldSeekTo = -1;

    private MediaPlayer player;
    private AudioplayersPlugin ref;

    WrappedMediaPlayer(AudioplayersPlugin ref, String playerId, MediaPlayer.OnErrorListener errorListener) {
        this.ref = ref;
        this.playerId = playerId;
        this.errorListener = errorListener;
    }

    /**
     * Setter methods
     */

    @Override
    void setUrl(String url, boolean isLocal) {
        if (!objectEquals(this.url, url)) {
            this.url = url;
            if (this.released) {
                this.player = createPlayer();
                this.released = false;
            } else if (this.prepared) {
                this.player.reset();
                this.prepared = false;
                this.playing = false;
            }

            this.setSource(url);
            this.player.setVolume((float) volume, (float) volume);
            this.player.setLooping(this.releaseMode == ReleaseMode.LOOP);
        }
    }

    @Override
    void setVolume(double volume) {
        if (this.volume != volume) {
            this.volume = volume;
            if (!this.released) {
                this.player.setVolume((float) volume, (float) volume);
            }
        }
    }

    @Override
    void configAttributes(boolean respectSilence, boolean stayAwake, Context context) {
        if (this.respectSilence != respectSilence) {
            this.respectSilence = respectSilence;
            if (!this.released) {
                setAttributes(player);
            }
        }
        if (this.stayAwake != stayAwake) {
            this.stayAwake = stayAwake;
            if (!this.released && this.stayAwake) {
                this.player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            }
        }
    }

    @Override
    void setReleaseMode(ReleaseMode releaseMode) {
        if (this.releaseMode != releaseMode) {
            this.releaseMode = releaseMode;
            if (!this.released) {
                this.player.setLooping(releaseMode == ReleaseMode.LOOP);
            }
        }
    }

    @Override
    void setOnPreparedCallback(MediaPlayer.OnPreparedListener listener) {
        player.setOnPreparedListener(listener);
    }

    /**
     * Getter methods
     */

    @Override
    int getDuration() {
        if (prepared && !released) {
            return this.player.getDuration();
        } else {
            return 0;
        }
    }

    @Override
    int getCurrentPosition() {
        if (prepared && !released) {
            return this.player.getCurrentPosition();
        } else {
            return 0;
        }
    }

    @Override
    String getPlayerId() {
        return this.playerId;
    }

    @Override
    boolean isActuallyPlaying() {
        return this.playing && this.prepared && !released;
    }

    /**
     * Playback handling methods
     */

    @Override
    void play() {
        if (!this.playing) {
            this.playing = true;
            if (this.released) {
                this.released = false;
                this.player = createPlayer();
                this.setSource(url);
                prepare();
            } else if (this.prepared) {
                this.player.start();
                this.ref.handleIsPlaying(this);
            }
        }
    }

    @Override
    void stop() {
        if (this.released || !this.prepared) {
            return;
        }
        if (releaseMode != ReleaseMode.RELEASE) {
            if (this.playing) {
                this.playing = false;
                this.player.pause();
                this.player.seekTo(0);
            }
        } else {
            this.release();
        }
    }

    @Override
    void release() {
        if (this.released) {
            return;
        }

        if (this.playing) {
            this.player.stop();
        }
        this.player.reset();
        this.player.release();
        this.player = null;

        this.prepared = false;
        this.released = true;
        this.playing = false;
    }

    @Override
    void prepare() {
        if (!prepared && player != null) {
            try {
                player.prepareAsync();
            } catch (IllegalStateException exc) {
                exc.printStackTrace();
            }
        }

    }

    @Override
    void pause() {
        if (this.playing) {
            this.playing = false;
            //To avoid state exceptions if player is not prepared yet.
            if (this.prepared) {
                this.player.pause();
            }
        }
    }

    // seek operations cannot be called until after
    // the player is ready.
    @Override
    void seek(int position) {
        if (this.prepared)
            this.player.seekTo(position);
        else
            this.shouldSeekTo = position;
    }

    /**
     * MediaPlayer callbacks
     */

    @Override
    public void onPrepared(final MediaPlayer mediaPlayer) {
        Log.d("debug", "onPrepared");
        this.prepared = true;
        ref.handleDuration(this);
        if (this.playing) {
            this.player.start();
            ref.handleIsPlaying(this);
        }
        if (this.shouldSeekTo >= 0) {
            this.player.seekTo(this.shouldSeekTo);
            this.shouldSeekTo = -1;
        }
    }

    @Override
    public void onCompletion(final MediaPlayer mediaPlayer) {
        if (releaseMode != ReleaseMode.LOOP) {
            this.stop();
        }
        ref.handleCompletion(this);
    }

    /**
     * Internal logic. Private methods
     */

    private MediaPlayer createPlayer() {
        MediaPlayer player = new MediaPlayer();
        player.setOnErrorListener(this);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        setAttributes(player);
        player.setVolume((float) volume, (float) volume);
        player.setLooping(this.releaseMode == ReleaseMode.LOOP);
        return player;
    }

    private void setSource(String url) {
        try {
            this.player.setDataSource(url);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to access resource", ex);
        }
    }

    @SuppressWarnings("deprecation")
    private void setAttributes(MediaPlayer player) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(respectSilence ? AudioAttributes.USAGE_NOTIFICATION_RINGTONE : AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            );
        } else {
            // This method is deprecated but must be used on older devices
            player.setAudioStreamType(respectSilence ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return errorListener.onError(mp, what, extra);
    }

}
