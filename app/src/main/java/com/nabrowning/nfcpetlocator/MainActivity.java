package com.nabrowning.nfcpetlocator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * Activity for reading data from an NDEF Tag.
 *
 * @author Ralf Wondratschek
 *
 */
public class MainActivity extends Activity {

    public static final String TAG = "NfcDemo";

    private TextView petNameTV;
    private TextView petTypeTV;
    private TextView ownerNameTV;
    private TextView addressTV;
    private TextView emailTV;
    private TextView phoneTV;
    private TextView locationTV;
    private String phoneNumber;
    private LocationManager locationManager;
    private double longitude;
    private double latitude;
    private List<Address> addresses;
    private Address address;
    public Geocoder geocoder;
    public String petLocation;

    private static final int INITIAL_REQUEST=1337;

    private static final String[] LOCATION_PERMS={
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private NfcAdapter nfcAdapter;
    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String MIME_CONTACT = "text/vcard";
    public static final String URL_STRING = "http://cs.coloradocollege.edu/~cp341mobile/cgi-bin/nfcpetlocator.cgi";
    public static final String ACTION_REPORT = "reportPet";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        petNameTV = (TextView) findViewById(R.id.nameText);
        petTypeTV = (TextView) findViewById(R.id.animalText);
        ownerNameTV = (TextView) findViewById(R.id.ownerText);
        addressTV = (TextView) findViewById(R.id.addressText);
        emailTV = (TextView) findViewById(R.id.emailText);
        phoneTV = (TextView) findViewById(R.id.numberText);
        locationTV = (TextView) findViewById(R.id.locationText);

//        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
//            requestPermissions(LOCATION_PERMS, INITIAL_REQUEST);
//        }

        getLocation();


        phoneNumber = null;

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (nfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;

        }

        if (!nfcAdapter.isEnabled()) {
            petNameTV.setText("NFC is disabled.");
        } else {
            petNameTV.setText("yay");
        }

        handleIntent(getIntent());
    }

//    private boolean canAccessLocation() {
//        return(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION));
//    }
//
//
//    private boolean hasPermission(String perm) {
//        return(PackageManager.PERMISSION_GRANTED==checkSelfPermission(perm));
//    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();
        Log.d(TAG, "intent type: " + type);

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);

            }
            else if (MIME_CONTACT.equals(type)){
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask().execute(tag);
            }
            else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    new NdefReaderTask().execute(tag);
                    break;
                }
            }
        }

    }

    public void dialPhoneNumber(View view) {
        if (phoneNumber != null){
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phoneNumber));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        }
    }

    private void getLocality(Location location){
        geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
        if(location != null){
            String locality = "";
            longitude = location.getLongitude();
            latitude = location.getLatitude();
            try{
                addresses = geocoder.getFromLocation(latitude, longitude, 1);
                address = addresses.get(0);
                if(address.getAddressLine(0) != null){
                    locality += address.getAddressLine(0)+" ";
                    locality += address.getAddressLine(1);
                }
                else{
                    locality += address.getLocality();
                }
//                Log.d("LOCALITY: ", locality);
                locationTV.setText("Your address: "+ locality);
                petLocation = locality;
//                Toast t = Toast.makeText(getApplicationContext(), locality, Toast.LENGTH_LONG);
//                t.show();
            }
            catch (Exception e) {
                System.out.println("Locality error: "+ e);
            }
//            longitude = location.getLongitude();
//            latitude = location.getLatitude();
//            Toast t = Toast.makeText(getApplicationContext(),
//                    String.format("long: " + longitude + "\nlat: " + latitude), Toast.LENGTH_LONG);
//            t.show();
        }
        else{
            Toast t = Toast.makeText(getApplicationContext(), "NO LAST LOCATION", Toast.LENGTH_SHORT);
            t.show();

        }

    }

    public void changeToSearch(View view){
        Intent intent = new Intent(getApplicationContext(), FindPet.class);
        startActivity(intent);
    }

    public void getLocation() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        try{
            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            getLocality(lastLocation);
        }
        catch (SecurityException e){
            System.out.print("Location Error: "+ e);
        }
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                getLocality(location);
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };

        try{
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
        }
        catch (SecurityException e){
            System.out.print(e);
        }
    }

    private class RetrieveHTTPTask extends AsyncTask<String, Void, String>{

        protected String doInBackground(String... urlStrings){
            String urlString = urlStrings[0];
            try{
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                Scanner myScanner = new Scanner(in);
                String result = "";
                while(myScanner.hasNext()){
                    result += myScanner.next()+"";
                }
                in.close();
                return result;
            }catch(Exception e){
                return "ERROR: " + e;
            }
        }

        protected void onPostExecute(String result){
            if(result != null){
                Toast.makeText(getApplicationContext(), "Reported to server", Toast.LENGTH_LONG).show();
//                finish();
            }
        }

    }

    private class NdefReaderTask extends AsyncTask<Tag, Void, HashMap<TextView, String>>{

        protected String petName;
        protected String petType;

        @Override
        protected HashMap<TextView, String> doInBackground(Tag... params) {
            HashMap<TextView, String> petInfo = new HashMap<>();
            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            Log.d(TAG, "---------------- RECORDS -------------------");
            for (int i = 0; i < records.length; i++) {
                String mimeType = records[i].toMimeType();
                Log.d(TAG, i + ": Mime type: " + mimeType);
                if (i == 0 && mimeType.equals(MIME_TEXT_PLAIN)){
                    handleRecord(records[i], RecordField.PET_NAME, petInfo);
                }
                else if(i == 1 && mimeType.equals(MIME_TEXT_PLAIN)){
                    handleRecord(records[i], RecordField.PET_TYPE, petInfo);
                }
                else if(mimeType != null && mimeType.equals(MIME_CONTACT)){
                    handleRecord(records[i], RecordField.CONTACT, petInfo);
                }
            }

            return petInfo;
        }

        private void handleRecord(NdefRecord record, RecordField field, HashMap<TextView, String> petInfo){
            String recordText = "";
            try {
                recordText =  readText(record);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Unsupported Encoding", e);
            }
            switch(field){
                case PET_NAME:
                    petInfo.put(petNameTV, recordText);
                    petName = recordText;
                    break;

                case PET_TYPE:
                    petInfo.put(petTypeTV, recordText);
                    petType = recordText;
                    break;

                case CONTACT:
                    setContact(recordText, petInfo);
                    break;
            }

        }

        private void setContact(String contact, HashMap<TextView, String> petInfo){
            String[] components = contact.split("\\r?\\n");
            for(String component : components){
                if(component.contains("FN")){
                    petInfo.put(ownerNameTV, component.substring(3));
                }
                else if(component.contains("ADR")){
                    component = component.replace(";", "");
                    component = component.replace("\\", "\n");
                    petInfo.put(addressTV, component.substring(4));
                }
                else if(component.contains("TEL")){
                    phoneNumber = component.substring(4);
                    petInfo.put(phoneTV, phoneNumber);
                }
                else if (component.contains("EMAIL")){
                    petInfo.put(emailTV, component.substring(6));
                }
            }
        }


        private String readText(NdefRecord record) throws UnsupportedEncodingException {
            String utf8 = "UTF-8";
            String utf16 = "UTF-16";
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? utf8 : utf16;

            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text

            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        @Override
        protected void onPostExecute(HashMap<TextView, String> result) {
            if (result != null) {
                for(Map.Entry<TextView, String> info : result.entrySet()){
                    info.getKey().setText(info.getValue());
                }
                try{
                    new RetrieveHTTPTask().execute(URL_STRING+"?action="+ACTION_REPORT+"&name="+petName+"&type="+petType+"&location="+ URLEncoder.encode(petLocation,"UTF-8"));
                    Toast.makeText(getApplicationContext(),"Reporting to server", Toast.LENGTH_LONG).show();
                }catch(Exception e){
                    System.out.println("Encoding error: " + e);
                }
            }
        }
    }

    private enum RecordField{
        PET_NAME, PET_TYPE, CONTACT, SMS;
    }

}
