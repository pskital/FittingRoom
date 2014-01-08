package androidstudio.pl.fittingroom;


import android.database.Cursor;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DownloadSettingsTask extends AsyncTask<String, Integer, Boolean> {
    private final static String LOG_TAG = "DownloadSettingsTask";
    private final FittingRoom fittingRoom;
    private String alertUrl;
    private Handler handler = new Handler();
    public DownloadAlertask downloadAlertTask;
    public List<String> alertRoomNameList = new ArrayList<String>();
    public List<String> alertRoomStatusList = new ArrayList<String>();

    public DownloadSettingsTask(FittingRoom fittingRoom) {
        this.fittingRoom = fittingRoom;
    }

    @Override
    protected void onPreExecute() {
        Log.w(LOG_TAG, "onPreExecute");
        fittingRoom.progressBar.setVisibility(View.VISIBLE);
        if (!fittingRoom.internetIsAvailable()) {
            Toast.makeText(this.fittingRoom,
                    "Proszę połączyć się z siecią WIFI o adresie: 5C:0A:5B:FC:2C:0E",
                    Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "No internet connection");
            cancel(true);
            fittingRoom.finish();
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        Log.w(LOG_TAG, "onCancelled");
        fittingRoom.progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    protected Boolean doInBackground(String... url) {
        final HttpClient client = AndroidHttpClient.newInstance("Android");
        final HttpGet getRequest = new HttpGet(url[0]);
        InputStream inputStream = null;
        try {
            final HttpResponse response = client.execute(getRequest);
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                Log.w(LOG_TAG, "Error " + statusCode + " while retrieving data from " + url[0]);
                return false;
            }
            Cursor cursor = fittingRoom.mDatabase.getVersion();
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    inputStream = entity.getContent();
                    final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "iso-8859-1"), 8);
                    final StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    final JSONObject jsonObject = new JSONObject(stringBuilder.toString());
                    final int version = jsonObject.getInt("Version");

                    if (cursor.moveToFirst()) {
                        if (version != cursor.getInt(0)) {
                            Log.w(LOG_TAG, "New version - start download data");
                            return downloadData(jsonObject);
                        } else {
                            Log.w(LOG_TAG, "Version: " + cursor.getInt(0));
                            return true;
                        }
                    } else {
                        Log.w(LOG_TAG, "No version in database");
                        return downloadData(jsonObject);
                    }
                } catch (Exception e) {
                    Log.w(LOG_TAG, "Error: " + e);
                    return false;
                } finally {
                    entity.consumeContent();
                    if (inputStream != null) inputStream.close();
                    if (cursor != null) cursor.close();
                }
            }
        } catch (Exception e) {
            getRequest.abort();
            Log.w(LOG_TAG, "Error while retrieving data from " + url[0], e);
            return false;
        } finally {
            ((AndroidHttpClient) client).close();
        }
        return true;
    }

    private Boolean downloadData(JSONObject jsonObject) {
        try {
            final JSONArray jsonArrayBackgroundImageSettings = jsonObject.getJSONArray("BackgroundImageSettings");
            final JSONObject BackgroundImageSettings = jsonArrayBackgroundImageSettings.getJSONObject(0);
            final String BackgroundMode = BackgroundImageSettings.getString("BackgroundMode");
            final String BackgroundImageUrl = BackgroundImageSettings.getString("BackgroundImage");

            HttpURLConnection connection = (HttpURLConnection) new URL(BackgroundImageUrl).openConnection();
            byte[] BackgroundImageBytes = IOUtils.toByteArray(connection.getInputStream());

            final JSONArray jsonArrayBackgroundColour = BackgroundImageSettings.getJSONArray("BackgroundColour");
            final JSONObject BackgroundColour = jsonArrayBackgroundColour.getJSONObject(0);

            final int Red = BackgroundColour.getInt("Red");
            final int Green = BackgroundColour.getInt("Green");
            final int Blue = BackgroundColour.getInt("Blue");

            /***************************jsonArrayIdleImageSettings*********************************/
            final JSONArray jsonArrayIdleImageSettings = jsonObject.getJSONArray("IdleImageSettings");
            final JSONObject IdleImageSettings = jsonArrayIdleImageSettings.getJSONObject(0);

            final String IdleImageUrl = IdleImageSettings.getString("IdleImage");

            connection = (HttpURLConnection) new URL(IdleImageUrl).openConnection();
            byte[] IdleImageBytes = IOUtils.toByteArray(connection.getInputStream());

            final int IdleImageWidth = IdleImageSettings.getInt("IdleImageWidth");
            final int IdleImageHeigh = IdleImageSettings.getInt("IdleImageHeigh");
            final int IdleImageUpdateRate = IdleImageSettings.getInt("IdleImageUpdateRate");
            final String ShowIdleImage = IdleImageSettings.getString("ShowIdleImage");

            /***************************jsonArrayIconImageSettings*********************************/
            final JSONArray jsonArrayIconImageSettings = jsonObject.getJSONArray("IconImageSettings");
            final JSONObject IconImageSettings = jsonArrayIconImageSettings.getJSONObject(0);

            final String IconImageUrl = IconImageSettings.getString("IconImage");

            connection = (HttpURLConnection) new URL(IconImageUrl).openConnection();
            byte[] IconImageBytes = IOUtils.toByteArray(connection.getInputStream());

            final int IconColumns = IconImageSettings.getInt("IconColumns");
            final int IconRows = IconImageSettings.getInt("IconRows");
            final int IconSpacing = IconImageSettings.getInt("IconSpacing");
            final int IconFadeoutTime = IconImageSettings.getInt("IconFadeoutTime");
            final int IconFlashTime = IconImageSettings.getInt("IconFlashTime");

            fittingRoom.mDatabase.insertBackgroundImageSettings(BackgroundMode, BackgroundImageBytes, Red, Green, Blue);
            fittingRoom.mDatabase.insertIdleImageSettings(IdleImageBytes, IdleImageWidth, IdleImageHeigh, IdleImageUpdateRate, ShowIdleImage);
            fittingRoom.mDatabase.insertIconImageSettings(IconImageBytes, IconColumns, IconRows, IconSpacing, IconFadeoutTime, IconFlashTime);
            fittingRoom.mDatabase.insertAlertFileUrl(jsonObject.getString("AlertFile"));
            fittingRoom.mDatabase.insertVersion(jsonObject.getInt("Version"));
        } catch (Exception e) {
            Log.e(LOG_TAG, "JSONException: " + e);
            return false;
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        Log.w(LOG_TAG, "onPostExecute");
        if (result) {
            fittingRoom.progressBar.setVisibility(View.INVISIBLE);
            fittingRoom.updateContentView();
            Toast.makeText(this.fittingRoom, "Update settings success", Toast.LENGTH_LONG).show();
            if (downloadAlertTask != null) {
                AsyncTask.Status diStatus = downloadAlertTask.getStatus();
                if (diStatus != AsyncTask.Status.FINISHED) {
                    return;
                }
            }
            final Cursor cursor = fittingRoom.mDatabase.getAlertUrl();
            if (cursor.moveToFirst()) {
                try {
                    alertUrl = cursor.getString(0);
                } finally {
                    cursor.close();
                }
            }

            downloadAlertTask = new DownloadAlertask();
            downloadAlertTask.execute();
        } else {
            Toast.makeText(this.fittingRoom, "Error downloading data", Toast.LENGTH_LONG).show();
            fittingRoom.progressBar.setVisibility(View.INVISIBLE);
        }

    }

    public void close() {
        handler.removeCallbacks(runnable);
        if (downloadAlertTask != null)
            downloadAlertTask.cancel(true);
        try {
            fittingRoom.mDatabase.close();
            Log.w(LOG_TAG, "Close database");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Field closeDatabase() " + e);
        }
    }

    private class DownloadAlertask extends AsyncTask<String, Integer, Boolean> {
        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Boolean doInBackground(String... strings) {
            final HttpClient client = AndroidHttpClient.newInstance("Android");
            final HttpGet getRequest = new HttpGet(alertUrl);
            InputStream inputStream = null;
            try {
                final HttpResponse response = client.execute(getRequest);
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK) {
                    Log.w(LOG_TAG, "Error " + statusCode + " while retrieving data from " + alertUrl);
                    return false;
                }
                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try {
                        inputStream = entity.getContent();
                        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "iso-8859-1"), 8);
                        final StringBuilder stringBuilder = new StringBuilder();
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            stringBuilder.append(line);
                        }
                        alertRoomNameList = new ArrayList<String>();
                        alertRoomStatusList = new ArrayList<String>();
                        final JSONObject jsonObject = new JSONObject(stringBuilder.toString());
                        final JSONArray jsonArrayRoomAlert = jsonObject.getJSONArray("RoomAlert");
                        for (int i = 0; i < jsonArrayRoomAlert.length(); i++) {
                            JSONObject roomAlertPbject = jsonArrayRoomAlert.getJSONObject(i);
                            String roomName = roomAlertPbject.getString("RoomName");
                            String roomStatus = roomAlertPbject.getString("RoomStatus");
                            alertRoomNameList.add(roomName);
                            alertRoomStatusList.add(roomStatus);
                        }
                    } catch (Exception e) {
                        Log.w(LOG_TAG, "Error: " + e);
                        return false;
                    } finally {
                        entity.consumeContent();
                        if (inputStream != null) inputStream.close();
                    }
                }
            } catch (Exception e) {
                getRequest.abort();
                Log.w(LOG_TAG, "Error while retrieving data from " + alertUrl, e);
                return false;
            } finally {
                ((AndroidHttpClient) client).close();
            }
            return true;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Toast.makeText(fittingRoom, "Update rooms success", Toast.LENGTH_LONG).show();
                fittingRoom.gridViewAdapter.notifyDataSetChanged();
            } else {
                Toast.makeText(fittingRoom, "Error updates rooms", Toast.LENGTH_LONG).show();
            }
            handler.postDelayed(runnable, 1000);
        }
    }

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            downloadAlertTask = new DownloadAlertask();
            downloadAlertTask.execute();
        }
    };
}
