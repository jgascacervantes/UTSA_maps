package ts.mapapp;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.NetworkInfo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.net.ConnectivityManager;
import android.widget.Toast;

import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;


import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, DownloadCallback,ActivityCompat.OnRequestPermissionsResultCallback {
    private NetworkFragment mNetworkFragment;
    private boolean mDownloading = false;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private GoogleMap mMap;
    private Polyline mShortestPath;
    DatabaseTable db;
    private static final String TAG = "debug tag";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        //TODO: remove test url
        mNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), makeURL(100,101,200,201));
        db = new DatabaseTable(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            try {
                Cursor c = db.getWordMatches(query, null);
                LatLng searched = new LatLng(c.getDouble(1), c.getDouble(2));
                mMap.moveCamera(CameraUpdateFactory.newLatLng(searched));
            } catch (NullPointerException e) {
            }
        }
    }



    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        boolean success = mMap.setMapStyle(new MapStyleOptions(getResources()
                .getString(R.string.style_json)));
        if (!success) {
            Log.e(TAG, "Style parsing failed.");
        }
        enableMyLocation();
        //TODO:tests
        //String test = "{\"result\":\"ok\",\"message\":[{\"1\":{\"from_lat\":29.582521,\"from_lng\":-98.61959,\"to_lat\":29.583156,\"to_lng\":-98.61841}},{\"2\":{\"from_lat\":29.583156,\"from_lng\":-98.61841,\"to_lat\":29.585619,\"to_lng\":-98.619204}},{\"3\":{\"from_lat\":29.585619,\"from_lng\":-98.619204,\"to_lat\":29.584406,\"to_lng\":-98.618302}}]}";
        //mShortestPath = mMap.addPolyline(stringJSONToPolyLine(test));
        getPath(100,101,200,201);

        LatLng utsa = new LatLng(29.5830, -98.6197);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(utsa));
        mMap.setMinZoomPreference(16);


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);

        // Associate searchable configuration with the SearchView

        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView =
                (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getComponentName()));

        return true;
    }


    private void getPath(double sourcelat, double sourcelng, double destlat, double destlng) {
        if (!mDownloading && mNetworkFragment != null) {
            // Execute the async download.
            mNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), makeURL(sourcelat, sourcelng, destlat, destlng));
            mNetworkFragment.startDownload();
            mDownloading = true;
        }
    }

    @Override
    public void updateFromDownload(String result) {

        if (result != null) {
            PolylineOptions resultPath = stringJSONToPolyLine(result);
            mShortestPath = mMap.addPolyline(resultPath);
        } else {
            Toast.makeText(this.getApplicationContext(), "Connection Error", Toast.LENGTH_SHORT);
        }
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo;
    }

    @Override
    public void finishDownloading() {
        mDownloading = false;
        if (mNetworkFragment != null) {
            mNetworkFragment.cancelDownload();
        }
    }

    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // You can add UI behavior for progress updates here.
            case Progress.ERROR:
                Log.d("PROGRESS", "ERR");
                break;
            case Progress.CONNECT_SUCCESS:
                Log.d("PROGRESS", "SUCCESS");
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                Log.d("PROGRESS", "GETINPUT");
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                Log.d("PROGRESS", "INPUTIP");
                //mDataText.setText("" + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                Log.d("PROGRESS", "PROCESSED");
                break;
        }
    }

    public String makeURL(double sourcelat, double sourcelng, double destlat,
                          double destlng) {
        StringBuilder urlString = new StringBuilder();
        urlString.append("http://easel2.fulgentcorp.com:8081/getPath?api_key=gibson");
        urlString.append("&from_lat=");
        urlString.append(Double.toString(sourcelat));
        urlString.append("&from_lng=");
        urlString.append(Double.toString(sourcelng));
        urlString.append("&to_lat=");
        urlString.append(Double.toString(destlat));
        urlString.append("&to_lng=");
        urlString.append(Double.toString(destlng));
        return urlString.toString();
    }

    public PolylineOptions stringJSONToPolyLine(String resultString){
        Log.d("RESPONSE", resultString); //TODO: this SHOULD contain the JSON
        PolylineOptions result = new PolylineOptions().width(15);
        JSONObject resultJSON;
        try {
            resultJSON = new JSONObject(resultString);
            JSONArray lineSegments = resultJSON.getJSONArray("message");
            for(int i = 0; i < lineSegments.length(); i++) {
                JSONObject segment = lineSegments.getJSONObject(i);
                JSONObject latLongs = segment.getJSONObject(segment.keys().next());
                double fromLat = latLongs.getDouble("from_lat");
                double fromLng = latLongs.getDouble("from_lng");
                double toLat = latLongs.getDouble("to_lat");
                double toLng = latLongs.getDouble("to_lng");

                result.add(new LatLng(fromLat, fromLng)).add(new LatLng(toLat,toLng));
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result;
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            mMap.setMyLocationEnabled(true);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
        }
    }
}
