package com.example.appforfunction;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主界面
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnGoToLogin).setOnClickListener(this);
        findViewById(R.id.btnGoToChat).setOnClickListener(this);
        findViewById(R.id.btnGoToList).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnGoToLogin) {
            startActivity(new Intent(this, LoginActivity.class));
        } else if (id == R.id.btnGoToChat) {
            startActivity(new Intent(this, ChatActivity.class));
        } else if (id == R.id.btnGoToList) {
            startActivity(new Intent(this, ListActivity.class));
        }
    }
}