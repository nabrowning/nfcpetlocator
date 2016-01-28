package com.nabrowning.nfcpetlocator;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    private TextView smsTV;
    private String phoneNumber;

    private NfcAdapter nfcAdapter;
    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String MIME_CONTACT = "text/vcard";

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
        smsTV = (TextView) findViewById(R.id.textSMS);

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

    private class NdefReaderTask extends AsyncTask<Tag, Void, HashMap<TextView, String>>{


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
                else{
                    handleRecord(records[i], RecordField.SMS, petInfo);
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
                    break;

                case PET_TYPE:
                    petInfo.put(petTypeTV, recordText);
                    break;

                case CONTACT:
                    setContact(recordText, petInfo);
                    break;

                case SMS:
                    petInfo.put(smsTV, recordText);
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
            }
        }
    }

    private enum RecordField{
        PET_NAME, PET_TYPE, CONTACT, SMS;
    }

}
