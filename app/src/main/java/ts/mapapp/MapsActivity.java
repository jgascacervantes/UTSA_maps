package ts.mapapp;

import ts.mapapp.R;//really frickin important bcuz it lets us call on xml elements
import android.R.id;

import java.util.ArrayList;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.location.LocationManager;
import android.location.LocationListener;
import android.location.Location;
import android.os.AsyncTask;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.net.ConnectivityManager;
import android.widget.CheckBox;
import android.widget.Toast;

import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.heatmaps.HeatmapTileProvider;


import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, DownloadCallback, ActivityCompat.OnRequestPermissionsResultCallback {
    private NetworkFragment mNetworkFragment;
    private  boolean mDownloading = false;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private volatile Integer callbackType = 0;// 0 available, 1 getpath, 2 gettraffic, 3 logposition

    private GoogleMap mMap;
    private Polyline mShortestPath;
    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;
    DatabaseTable db;
    private static final String TAG = "debug tag";
    private LatLng mDestination = new LatLng(0.0,0.0);
    LocationManager locM;
    LocationListener locL;
    Location currentLoc;

    CheckBox followCheckBox;
    CheckBox heatMapCheckBoc;
    int heatMapFlag = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), makeURL("getPath",100,101,200,201));

        followCheckBox = (CheckBox) findViewById(R.id.followCheckBox);
        heatMapCheckBoc = (CheckBox) findViewById(R.id.heatMapCheckBox);

        locM = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        try {
            currentLoc = locM.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(currentLoc != null)
                Toast.makeText(getApplicationContext(),"Location Found",Toast.LENGTH_LONG).show();
            else
                Toast.makeText(getApplicationContext(),"Location Not Found",Toast.LENGTH_LONG).show();
        }
        catch(SecurityException e)
        {
            Toast.makeText(getApplicationContext(),"Error: " + e.getMessage(),Toast.LENGTH_LONG).show();
        }
        // Define a listener that responds to location updates
        locL = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                currentLoc = location;
                logPosition(currentLoc.getLatitude(),currentLoc.getLongitude());
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(Math.abs(currentLoc.getLatitude() - mDestination.latitude) <  1E-4 && Math.abs(currentLoc.getLongitude() - mDestination.longitude) <1E-4){
                    Toast.makeText(getApplicationContext(), "You Have Reached Your Destination!",
                            Toast.LENGTH_LONG).show();
                    mMap.clear();
                }
                if(followCheckBox.isChecked()) {
                    mShortestPath.remove();
                    getPath(currentLoc.getLatitude(), currentLoc.getLongitude(), mDestination.latitude, mDestination.longitude);
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(currentLoc.getLatitude(), currentLoc.getLongitude())));
                }
                if(heatMapCheckBoc.isChecked() && heatMapFlag == 0){
                    getTraffic();
                    heatMapFlag = 1;
                }
                if(!heatMapCheckBoc.isChecked()){
                    heatMapFlag = 0;
                    mOverlay.remove();
                }

            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        try {
            locM.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locL);
        }
        catch(SecurityException e)
        {
            Toast.makeText(getApplicationContext(),"Error: " + e.getMessage(),Toast.LENGTH_LONG).show();
        }
        getTraffic();
        db = new DatabaseTable(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            MarkerOptions pin = new MarkerOptions();
            String query = intent.getStringExtra(SearchManager.QUERY);
            try {
                Cursor c = db.getWordMatches(query, null);
                Log.d(TAG, "handleIntent: cursor " + c.getString(0) + " " + c.getString(1) + " " + c.getString(2) + " " + c.getString(3) + " " + c.getString(4));
                LatLng searched = new LatLng(c.getDouble(4), c.getDouble(3));  // You may need to fix these indices, look at the logcat
                mMap.clear();
                mMap.addMarker(pin.position(searched).title(query));
                mMap.animateCamera(CameraUpdateFactory.newLatLng(searched));
                //getTraffic();
                getPath(currentLoc.getLatitude(),currentLoc.getLongitude(),searched.latitude,searched.longitude);
                mDestination = new LatLng(searched.latitude,searched.longitude);
            } catch (NullPointerException e) {
                Toast.makeText(getApplicationContext(),"Error: " + e.getMessage(),Toast.LENGTH_LONG).show();
            }
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            MarkerOptions pin = new MarkerOptions();
            String query = data.toString();
            try {
                Cursor c = db.getWordMatches(query, null);
                Log.d(TAG, "handleIntent: cursor " + c.getString(0) + " " + c.getString(1) + " " + c.getString(2) + " " + c.getString(3) + " " + c.getString(4));
                LatLng searched = new LatLng(c.getDouble(4), c.getDouble(3));  // You may need to fix these indices, look at the logcat
                mMap.clear();
                mMap.addMarker(pin.position(searched).title(query));
                mMap.animateCamera(CameraUpdateFactory.newLatLng(searched));
                //getTraffic();
                getPath(currentLoc.getLatitude(),currentLoc.getLongitude(),searched.latitude,searched.longitude);
                mDestination = new LatLng(searched.latitude,searched.longitude);
            } catch (NullPointerException e) {
                Toast.makeText(getApplicationContext(),"Error: " + e.getMessage(),Toast.LENGTH_LONG).show();
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

        LatLng utsa = new LatLng(29.5830, -98.6197);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(utsa));
        mMap.setMinZoomPreference(16);//16
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


        if (mNetworkFragment != null) {
            //mNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), makeURL("getPath", sourcelat, sourcelng, destlat, destlng));
            mNetworkFragment.setmUrlString(makeURL("getPath", sourcelat, sourcelng, destlat, destlng));
            mNetworkFragment.startDownload();
            mDownloading = true;
            callbackType = 1;
        }
    }

    private void getTraffic(){


        if (mNetworkFragment != null) {
            //mNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), "http://easel2.fulgentcorp.com:8081/getTraffic?api_key=gibson");
            mNetworkFragment.setmUrlString("http://easel2.fulgentcorp.com:8081/getTraffic?api_key=gibson");
            mNetworkFragment.startDownload();
            mDownloading = true;
            callbackType = 2;
        }


    }

    private void logPosition(double lat, double lng){
        if ( mNetworkFragment != null) {
            //mNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), makeURL("logPosition", lat, lng, lat, lng));
            mNetworkFragment.setmUrlString(makeURL("logPosition", lat, lng, lat, lng));
            mNetworkFragment.startDownload();
            mDownloading = true;
            callbackType = 3;
        }
    }

    //Networking Code begin
    @Override
    public void updateFromDownload(String result) {

        if (result != null) {
            Log.d("UPDATE", "CB" + callbackType +result );
            switch (callbackType) {
                case 1:
                    PolylineOptions resultPath = stringJSONToPolyLine(result);
                    mShortestPath = mMap.addPolyline(resultPath.color(Color.YELLOW));
                    break;
                case 2:
                    try {
                        ArrayList<LatLng> trafficPoints = stringToHeatmap(result);
                        mProvider = new HeatmapTileProvider.Builder()
                                .data(trafficPoints).radius(50)
                                .build();
                        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
                    } catch (JSONException e){
                        e.printStackTrace();
                    }
                    break;
                case 3:
                    //TODO SUCCESS MESSAGE
                    break;
            }
        } else {
            Toast.makeText(this.getApplicationContext(), "Connection Error", Toast.LENGTH_SHORT).show();
        }
        callbackType = 0;
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo     networkInfo = connectivityManager.getActiveNetworkInfo();
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

    public String makeURL(String func, double sourcelat, double sourcelng, double destlat,
                          double destlng) {
        StringBuilder urlString = new StringBuilder();
        if(func.equals("getPath")){
            urlString.append("http://easel2.fulgentcorp.com:8081/getPath?api_key=gibson");
            urlString.append("&from_lat=");
            urlString.append(Double.toString(sourcelat));
            urlString.append("&from_lng=");
            urlString.append(Double.toString(sourcelng));
            urlString.append("&to_lat=");
            urlString.append(Double.toString(destlat));
            urlString.append("&to_lng=");
            urlString.append(Double.toString(destlng));
        } else if(func.equals("logPosition")){
            urlString.append("http://easel2.fulgentcorp.com:8081/logPosition?api_key=gibson");
            urlString.append("&lat=");
            urlString.append(Double.toString(sourcelat));
            urlString.append("&lng=");
            urlString.append(Double.toString(sourcelng));
        }
        return urlString.toString();
    }

    public PolylineOptions stringJSONToPolyLine(String resultString){

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

    private ArrayList<LatLng> stringToHeatmap(String resultString) throws JSONException{
        Log.d("RESPONSE", resultString);
        ArrayList<LatLng> trafficPoints = new ArrayList<LatLng>();
        JSONObject resultJSON = new JSONObject(resultString);
        JSONArray lineSegments = resultJSON.getJSONArray("message");
        for(int i = 0; i < lineSegments.length(); i++) {
            JSONObject segment = lineSegments.getJSONObject(i);
            double fromLat = segment.getDouble("lat");
            double fromLng = segment.getDouble("lng");

            trafficPoints.add(new LatLng(fromLat, fromLng));
        }
        for(int i = 0; i < trafficPoints.size(); i++){
            Log.d("POINTS", trafficPoints.get(i).toString());
        }
        return trafficPoints;
    }
    //Networking Code end

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
        }
    }
}
