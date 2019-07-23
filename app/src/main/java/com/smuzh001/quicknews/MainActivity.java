package com.smuzh001.quicknews;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    ArrayList<String> news_title = new ArrayList<>();
    ArrayList<String> news_content = new ArrayList<>();

    //adapts news_title for the ListView
    ArrayAdapter arrayAdapter;
    SQLiteDatabase quicknewsDB;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        quicknewsDB = this.openOrCreateDatabase("articles", MODE_PRIVATE, null);
        quicknewsDB.execSQL("CREATE TABLE IF NOT EXISTS articles(id INTEGER PRIMARY KEY, articleId INTEGER, title VARCHAR, content VARCHAR)");


        //start a new download task and execute the hacker-news top 500 stories should return the ID's for the stories.
        DownloadTask task = new DownloadTask();
        try{
            //task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");
        }catch(Exception e){
            e.printStackTrace();
        }

        ListView listView = findViewById(R.id.listView);
        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, news_title);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getApplicationContext(), NewsActivity.class);
                intent.putExtra("newsContent", news_content.get(position));

                startActivity(intent);
            }
        });
        updateListView();
    }
    public void updateListView(){
        Cursor c = quicknewsDB.rawQuery("SELECT * FROM articles", null);
        int contentIndex = c.getColumnIndex("content");
        int titleIndex = c.getColumnIndex("title");

        if(c.moveToFirst()){
            news_title.clear();
            news_content.clear();
            do{
                news_title.add(c.getString(titleIndex));
                news_content.add(c.getString(contentIndex));

            }while(c.moveToNext());
            arrayAdapter.notifyDataSetChanged();

        }

    }


    //Working with an api you need to Extend the Async Task so that we can download the appropriate data.
    //DownloadTask gets the list of ID's of all the top stories and to get the specific stories.
    public class DownloadTask extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... urls) {

            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;

            try{
                url = new URL(urls[0]);

                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = urlConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

                int data = inputStreamReader.read();
                while(data != -1){
                    char current = (char) data;
                    result += current;
                    data = inputStreamReader.read();
                }

                //Process the URL string results as a JSON array so we could quickly access each item.
                JSONArray jsonArray = new JSONArray(result);
                int numberOfItems = 20;
                if(jsonArray.length() < 20){
                    numberOfItems = jsonArray.length();
                }

                quicknewsDB.execSQL("DELETE FROM articles");

                //for each articleID add it to the URL supplanting the articleID.
                for(int i = 0; i < numberOfItems; i++){
                    String articleID = jsonArray.getString(i);
                    url = new URL("https://hacker-news.firebaseio.com/v0/item/"+articleID+".json?print=pretty");
                    //with this new url we redo and read the information as above to get the id's
                    urlConnection = (HttpURLConnection) url.openConnection();
                    inputStream = urlConnection.getInputStream();
                    inputStreamReader = new InputStreamReader(inputStream);

                    String articleInfo = "";
                    data = inputStreamReader.read();
                    while(data != -1){
                        char current = (char) data;
                        articleInfo += current;
                        data = inputStreamReader.read();
                    }
                    //Log.i("Article Info", articleInfo);
                    //we want to grab out the article title and link.
                    JSONObject jsonObject = new JSONObject(articleInfo);
                    //check that title isnt null and that the URL is there
                    if(!jsonObject.isNull("title") && !jsonObject.isNull("url")){
                        String articleTitle = jsonObject.getString("title");
                        String articleURL = jsonObject.getString("url");
                        Log.i("Title and URL", articleTitle+" "+articleURL);

                        //download the URL object
                        url = new URL(articleURL);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        inputStream = urlConnection.getInputStream();
                        inputStreamReader = new InputStreamReader(inputStream);

                        String articleContent = "";
                        data = inputStreamReader.read();
                        while(data != -1){
                            char current = (char) data;
                            articleContent += current;
                            data = inputStreamReader.read();
                        }
                        //returns HTML for each individual article website.
                        Log.i("HTML", articleContent);
                        //pass into database
                        String sql = "INSERT into articles (articleId, title, content) VALUES(?,?,?)";
                        SQLiteStatement statement = quicknewsDB.compileStatement(sql);
                        //pass in info this method protects our database incase one of the article items is jank.
                        statement.bindString(1, articleID);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleContent);
                        statement.execute();
                    }


                }

                Log.i("URL Results", result);
                return result;

            }catch(Exception e){
                e.printStackTrace();
            }

            return null;
        }
        //after everything is finished update the listview.
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            updateListView();
        }
    }
}
