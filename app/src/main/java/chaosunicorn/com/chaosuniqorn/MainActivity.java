package chaosunicorn.com.chaosuniqorn;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE;
import static chaosunicorn.com.chaosuniqorn.Helpers.UuidToByteArray;
import static chaosunicorn.com.chaosuniqorn.Helpers.convertInputStreamToString;
import static chaosunicorn.com.chaosuniqorn.Helpers.integerToByteArray;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ChaosUnicornNetwork";
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanSettings mScanSettings;
    private ScanFilter mScanFilter;

    private final static String mServerAddress = "https://chaosunicorn.herokuapp.com/post";
    private final static String mUuidWhiteIbeacon = "C9:43:D8:4E:15:23";
    private final static String mUuidBluefruit = "D8:3C:B0:C0:DB:2D";

    private TextView mTextView1;
    private TextView mTextView2;
    private TextView mTextView3;

    private int mPlayerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView1 = (TextView) findViewById(R.id.textView1);
        mTextView2 = (TextView) findViewById(R.id.textView2);
        mTextView3 = (TextView) findViewById(R.id.textView3);
        mPlayerId = 0;

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        checkAccessLocation();
        setScanSettings();
        setScanFilter();

        // enable bluetooth before
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // check if you are connected or not
        if (isConnected()) {
            Log.d(TAG, "onCreate: " + "CONNECTED!");
        } else {
            Log.d(TAG, "onCreate: " + "NOT CONNECTED!");
        }

        WebView webView = (WebView) findViewById(R.id.webView);
        webView.loadUrl("http://10.200.18.88:8080");
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    public void startScanForBeacons()
    {
        mBluetoothLeScanner.startScan(Arrays.asList(mScanFilter), mScanSettings, mScanCallback);
    }

    public class WebAppInterface {
        Context mContext;

        /** Instantiate the interface and set the context */
        WebAppInterface(Context c) {
            mContext = c;
        }
        @JavascriptInterface
        public void playerIdSelected(int playerId) {
            mPlayerId = playerId;
            startScanForBeacons();
        }
    }

    private void checkAccessLocation() {
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect beacons.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    protected ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    double rssi = result.getRssi();

                    Log.d(TAG, "Distance: " + result.getDevice().getAddress() + " " + rssi);
                    if (result.getDevice().getAddress().equals(mUuidWhiteIbeacon)) {
                        mTextView1.setText(String.valueOf(rssi));
                        new HttpAsyncTask(mPlayerId, 0, (int)rssi).execute();
                    } else if (result.getDevice().getAddress().equals(mUuidBluefruit)) {
                        mTextView2.setText(String.valueOf(rssi));
                        new HttpAsyncTask(mPlayerId, 1, (int)rssi).execute();
                    } else { // smartphone beacon
                        mTextView3.setText(String.valueOf(rssi));
                        new HttpAsyncTask(mPlayerId, 2, (int)rssi).execute();
                    }
                }
            });
        }
    };

    private void setScanSettings() {
        ScanSettings.Builder mBuilder = new ScanSettings.Builder();
        mBuilder.setReportDelay(0);
        mBuilder.setMatchMode(MATCH_MODE_AGGRESSIVE);
        mBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        mScanSettings = mBuilder.build();
    }

    private void setScanFilter() {
        ScanFilter.Builder mBuilder = new ScanFilter.Builder();

        // the manufacturer data byte is the filter!
        final byte[] manufacturerData = new byte[]
                {
                        0, 0,

                        // uuid
                        0, 0, 0, 0,
                        0, 0,
                        0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0,

                        // major
                        0, 0,

                        // minor
                        0, 0,

                        0
                };

        // the mask tells what bytes in the filter need to match, 1 if it has to match, 0 if not
        final byte[] manufacturerDataMask = new byte[]
                {
                        0, 0,

                        // uuid
                        1, 1, 1, 1,
                        1, 1,
                        1, 1,
                        1, 1, 1, 1, 1, 1, 1, 0,

                        // major
                        1, 1,

                        // minor
                        1, 1,

                        0
                };

        String temp = "00000000-0000-0000-0000-000000000001";
        String realString = temp.replace("-", "");
        UUID tempUuid = new UUID(
                new BigInteger(realString.substring(0, 16), 16).longValue(),
                new BigInteger(realString.substring(16), 16).longValue());

        // copy UUID (with no dashes) into data array
        System.arraycopy(UuidToByteArray(tempUuid), 0, manufacturerData, 2, 16);

        // copy major into data array
        System.arraycopy(integerToByteArray(2), 0, manufacturerData, 18, 2);

        // copy minor into data array
        System.arraycopy(integerToByteArray(2), 0, manufacturerData, 20, 2);

        mBuilder.setManufacturerData(76, manufacturerData, manufacturerDataMask);
        mScanFilter = mBuilder.build();
    }

    public static String POST(String url, int devId, int locId, int distance) {
        InputStream inputStream = null;
        String result = "";
        try {

            // 1. create HttpClient
            HttpClient httpclient = new DefaultHttpClient();

            // 2. make POST request to the given URL
            HttpPost httpPost = new HttpPost(url);

            String json = "";

            // 3. build jsonObject
            JSONObject jsonObject = new JSONObject();
            jsonObject.accumulate("playerId", devId);
            jsonObject.accumulate("locationId", locId);
            jsonObject.accumulate("distance", distance);

            // 4. convert JSONObject to JSON to String
            json = jsonObject.toString();

            // 5. set json to StringEntity
            StringEntity se = new StringEntity(json);

            // 6. set httpPost Entity
            httpPost.setEntity(se);

            // 7. Set some headers to inform server about the type of the content
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            // 8. Execute POST request to the given URL
            HttpResponse httpResponse = httpclient.execute(httpPost);

            // 9. receive response as inputStream
            inputStream = httpResponse.getEntity().getContent();

            // 10. convert inputstream to string
            if (inputStream != null)
                result = convertInputStreamToString(inputStream);
            else
                result = "Did not work!";

        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
        }

        // 11. return result
        return result;
    }

    public boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return  networkInfo != null && networkInfo.isConnected();
    }

    private class HttpAsyncTask extends AsyncTask<String, Void, String> {
        private int mDevId;
        private int mLocId;
        private int mDistance;

        HttpAsyncTask(int devId, int locId, int distance) {
            mDevId = devId;
            mLocId = locId;
            mDistance = distance;
        }

        @Override
        protected String doInBackground(String... strings) {
            return POST(mServerAddress, mDevId, mLocId, mDistance);
        }

        @Override
        protected void onPostExecute(String result) {
        }
    }
}
