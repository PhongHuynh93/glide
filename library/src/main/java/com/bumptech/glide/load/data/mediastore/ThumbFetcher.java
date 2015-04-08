package com.bumptech.glide.load.data.mediastore;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.bumptech.glide.Logs;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.ExifOrientationStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link DataFetcher} implementation for {@link InputStream}s that loads data from thumbnail
 * files obtained from the {@link MediaStore}.
 */
public class ThumbFetcher implements DataFetcher<InputStream> {
  private final Context context;
  private final Uri mediaStoreImageUri;
  private final ThumbnailStreamOpener opener;
  private InputStream inputStream;

  public static ThumbFetcher buildImageFetcher(Context context, Uri uri) {
    return build(context, uri, new ImageThumbnailQuery());
  }

  public static ThumbFetcher buildVideoFetcher(Context context, Uri uri) {
    return build(context, uri, new VideoThumbnailQuery());
  }

  private static ThumbFetcher build(Context context, Uri uri, ThumbnailQuery query) {
    return new ThumbFetcher(context, uri, new ThumbnailStreamOpener(query));
  }

  // Visible for testing.
  ThumbFetcher(Context context, Uri mediaStoreImageUri, ThumbnailStreamOpener opener) {
    this.context = context;
    this.mediaStoreImageUri = mediaStoreImageUri;
    this.opener = opener;
  }

  @Override
  public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
    inputStream = openThumbInputStream();
    callback.onDataReady(inputStream);
  }

  private InputStream openThumbInputStream() {
    InputStream result = null;
    try {
      result = opener.open(context, mediaStoreImageUri);
    } catch (FileNotFoundException e) {
      if (Logs.isEnabled(Log.DEBUG)) {
        Logs.log(Log.DEBUG, "Failed to find thumbnail file", e);
      }
    }

    int orientation = -1;
    if (result != null) {
      orientation = opener.getOrientation(context, mediaStoreImageUri);
    }

    if (orientation != -1) {
      result = new ExifOrientationStream(result, orientation);
    }
    return result;
  }

  @Override
  public void cleanup() {
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (IOException e) {
        // Ignored.
      }
    }
  }

  @Override
  public void cancel() {
    // Do nothing.
  }

  @Override
  public Class<InputStream> getDataClass() {
    return InputStream.class;
  }

  @Override
  public DataSource getDataSource() {
    return DataSource.LOCAL;
  }

  static class VideoThumbnailQuery implements ThumbnailQuery {
    private static final String[] PATH_PROJECTION = {
      MediaStore.Video.Thumbnails.DATA
    };
    private static final String PATH_SELECTION =
        MediaStore.Video.Thumbnails.KIND + " = " + MediaStore.Video.Thumbnails.MINI_KIND
        + " AND " + MediaStore.Video.Thumbnails.VIDEO_ID + " = ?";

    @Override
    public Cursor query(Context context, Uri uri) {
      String videoId = uri.getLastPathSegment();
      return context.getContentResolver().query(
          MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
          PATH_PROJECTION,
          PATH_SELECTION,
          new String[] { videoId },
          null /*sortOrder*/);
    }
  }


  static class ImageThumbnailQuery implements ThumbnailQuery {
    private static final String[] PATH_PROJECTION = {
      MediaStore.Images.Thumbnails.DATA,
    };
    private static final String PATH_SELECTION =
        MediaStore.Images.Thumbnails.KIND + " = " + MediaStore.Images.Thumbnails.MINI_KIND
        + " AND " + MediaStore.Images.Thumbnails.IMAGE_ID + " = ?";

    @Override
    public Cursor query(Context context, Uri uri) {
      String imageId = uri.getLastPathSegment();
      return context.getContentResolver().query(
          MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
          PATH_PROJECTION,
          PATH_SELECTION,
          new String[] { imageId },
          null /*sortOrder*/);
    }
  }
}