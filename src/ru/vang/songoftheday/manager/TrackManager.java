package ru.vang.songoftheday.manager;

import org.json.JSONException;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore.Audio.Media;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import ru.vang.songoftheday.api.LastFM;
import ru.vang.songoftheday.api.Track;
import ru.vang.songoftheday.api.Vk;
import ru.vang.songoftheday.api.VkTrack;
import ru.vang.songoftheday.database.SongOfTheDayDbHelper;
import ru.vang.songoftheday.exceptions.VkApiException;
import ru.vang.songoftheday.model.WidgetUpdateInfo;

public class TrackManager {

    public static final String[] MEDIA_PROJECTION = {Media._ID, Media.ARTIST,
            Media.TITLE};

    private static final int MEDIA_ARTIST_INDEX = 1;

    private static final int MEDIA_TITLE_INDEX = 2;

    private static final String EMPTY_ARTIST = null;

    private transient final Context mContext;

    public TrackManager(final Context context) {
        mContext = context;
    }

    public VkTrack getVkTrack(final String artist, final String title)
            throws IOException, JSONException, VkApiException {
        final Vk vk = new Vk(mContext);
        final List<VkTrack> vkTracks = vk.searchAudio(artist, title);

        VkTrack vkTrack = null;
        if (!vkTracks.isEmpty()) {
            vkTrack = vkTracks.get(0);
            // Set artist and title to original because vk track can contain
            // trash
            vkTrack.setArtist(artist);
            vkTrack.setTitle(title);
        }

        return vkTrack;
    }

    public Track getTopTrack() throws IOException {
        final List<Track> topTracks = LastFM.getTopTracks();
        return getNewLastFmTrack(topTracks, EMPTY_ARTIST);
    }

    private Track getNewLastFmTrack(final List<Track> lastFmTracks,
            final String originalArtist) {
        Track track = null;
        if (lastFmTracks != null && !lastFmTracks.isEmpty()) {
            final SongOfTheDayDbHelper dbHelper = new SongOfTheDayDbHelper(mContext);
            // Shuffle list cause of tracks ordered by similarity
            Collections.shuffle(lastFmTracks, getRandom());
            for (Track lastFmTrack : lastFmTracks) {
                if (!(ifSameArtist(originalArtist, lastFmTrack.getArtist()) || dbHelper
                        .ifMbidExists(lastFmTrack.getId()))) {
                    track = lastFmTrack;
                    break;
                }
            }
        }
        return track;
    }

    private boolean ifSameArtist(final String originalArtist, final String foundArtist) {
        return originalArtist != null && originalArtist.equals(foundArtist);
    }

    public Track getLastFmTrack(final String artist, final String title)
            throws IOException {
        final List<Track> lastFmTracks = LastFM.getSimilarTracks(artist, title);
        final Track foundTrack = getNewLastFmTrack(lastFmTracks, artist);

        return foundTrack;
    }

    public void findTopTrackInfo(final WidgetUpdateInfo widgetInfo)
            throws VkApiException, IOException, JSONException {
        final boolean hasVkAccount = Vk.hasVkAccount(mContext);
        widgetInfo.setHasVkAccount(hasVkAccount);
        VkTrack vkTrack = null;
        Track track;
        do {
            track = getTopTrack();
            if (track != null) {
                widgetInfo.setTrack(track);
                if (hasVkAccount) {
                    vkTrack = getVkTrack(track.getArtist(), track.getTitle());
                }
            }
        } while ((vkTrack == null && hasVkAccount) || (track == null));
        if (hasVkAccount) {
            widgetInfo.setVkTrack(vkTrack);
        }
    }

    public void findSimilarTrackInfo(final Cursor cursor,
            final WidgetUpdateInfo widgetInfo) throws VkApiException,
            IOException, JSONException {
        final boolean hasVkAccount = Vk.hasVkAccount(mContext);
        widgetInfo.setHasVkAccount(hasVkAccount);
        VkTrack vkTrack = null;
        Track track = null;
        final int count = cursor.getCount();
        final int[] indexes = shuffleIndexes(count);
        for (int i = 0; i < count; i++) {
            cursor.moveToPosition(indexes[i]);
            final String originalTitle = cursor.getString(MEDIA_TITLE_INDEX);
            final String originalArtist = cursor.getString(MEDIA_ARTIST_INDEX);
            widgetInfo.setOriginalArtist(originalArtist);
            widgetInfo.setOriginalTitle(originalTitle);
            track = getLastFmTrack(originalArtist, originalTitle);
            if (track != null) {
                widgetInfo.setTrack(track);
                if (hasVkAccount) {
                    vkTrack = getVkTrack(track.getArtist(), track.getTitle());
                }
            }
            if (vkTrack != null || (track != null && !hasVkAccount)) {
                break;
            }
        }
        if (track != null) {
            final SongOfTheDayDbHelper dbHelper = new SongOfTheDayDbHelper(mContext);
            dbHelper.insertMbid(track.getId());
        }
        if (hasVkAccount) {
            widgetInfo.setVkTrack(vkTrack);
        }
    }

    private Random getRandom() {
        return new Random(System.currentTimeMillis());
    }

    private int[] shuffleIndexes(final int length) {
        final int[] indexes = new int[length];
        for (int i = 0; i < length; i++) {
            indexes[i] = i;
        }
        final Random random = getRandom();
        for (int i = 0; i < length; i++) {
            final int position = i + random.nextInt(length - i);
            final int buffer = indexes[i];
            indexes[i] = indexes[position];
            indexes[position] = buffer;
        }

        return indexes;
    }
}
