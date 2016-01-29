package com.nabrowning.nfcpetlocator;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Scanner;

public class FindPet extends AppCompatActivity {
    private EditText petName;
    private EditText petType;
    public static final String URL_STRING = "http://cs.coloradocollege.edu/~cp341mobile/cgi-bin/nfcpetlocator.cgi";
    public static final String ACTION_FIND = "findPet";
    Button btn_search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_pet);

        petName = (EditText) findViewById(R.id.et_petName);
        petType = (EditText) findViewById(R.id.et_petType);

        btn_search = (Button) findViewById(R.id.btn_search);
        btn_search.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                getPetLocation();
            }
        });
    }

    protected void getPetLocation(){
        String name = petName.getText().toString();
        String type = petType.getText().toString();

        new RetrieveHTTPTask().execute(URL_STRING+"?action="+ACTION_FIND+"&name="+name+"&type="+type);

    }

    private class RetrieveHTTPTask extends AsyncTask<String, Void, String> {

        protected String doInBackground(String... urlStrings){
            String urlString = urlStrings[0];
            try{
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                Scanner myScanner = new Scanner(in);
                String result = "";
                while(myScanner.hasNext()){
                    result += myScanner.next()+" ";
                }
                in.close();
                return result;
            }catch(Exception e){
                return "ERROR: " + e;
            }
        }

        protected void onPostExecute(String result){
            String addressToFind = "";
            if(result != null){
                System.out.println(result);
                String[] addressBits = result.split(" ");
                if(addressBits.length > 6) {
                    for (int i = 6; i < addressBits.length; i++) {
                        addressToFind += addressBits[i] + " ";
                    }
                    System.out.println(addressToFind);
                }
                String geoString = "geo:0,0?q="+addressToFind;

                Uri uri = Uri.parse(geoString);

                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        }

    }
}
