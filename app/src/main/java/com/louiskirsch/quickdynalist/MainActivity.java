package com.louiskirsch.quickdynalist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private DialogInterface.OnClickListener authStartListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            showTokenDialog();
            openTokenGenerationBrowser();
        }
    };

    private AlertDialog authDialog;
    private RequestQueue queue;
    private Spinner itemLocation;
    private EditText itemContents;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        itemLocation = findViewById(R.id.parent_spinner);
        itemContents = findViewById(R.id.item_content);
        submitButton = findViewById(R.id.btn_add_item);

        itemContents.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                updateSubmitEnabled();
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addItem(itemLocation.getSelectedItem().toString(),
                        itemContents.getText().toString());
                itemContents.getText().clear();
            }
        });

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.item_locations, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        itemLocation.setAdapter(adapter);

        queue = Volley.newRequestQueue(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSubmitEnabled();
        if (!isAuthenticated() && authDialog == null) {
            authenticate();
        } else {
            itemContents.requestFocus();
        }
    }

    private void updateSubmitEnabled() {
        boolean enabled = isAuthenticated() && !itemContents.getText().toString().isEmpty();
        submitButton.setEnabled(enabled);
    }

    private void addItem(String parent, String contents) {
        // TODO addd item not to inbox but to `parent`
        JSONObject payload;
        try {
            payload = createRequestPayload(null);
            payload.put("content", contents);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                "https://dynalist.io/api/v1/inbox/add",
                payload,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.getString("_code").equals("Ok")) {
                                Toast.makeText(MainActivity.this,
                                        R.string.add_item_success, Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                showValidationError();
                            }
                        } catch (JSONException e) {
                            showValidationError();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                        Toast.makeText(MainActivity.this,
                                R.string.add_item_error, Toast.LENGTH_SHORT).show();
                    }
                });
        queue.add(request);
    }

    private SharedPreferences getPreferences() {
        return this.getSharedPreferences("PREFERENCES", Context.MODE_PRIVATE);
    }

    private String getToken() {
        return getPreferences().getString("TOKEN", "NONE");
    }

    private boolean isAuthenticated() {
        return this.getPreferences().contains("TOKEN");
    }

    private void showTokenDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View view = LayoutInflater.from(this).inflate(R.layout.activity_auth, null);
        DialogInterface.OnClickListener tokenAcceptListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                EditText tokenField = view.findViewById(R.id.auth_token);
                String token = tokenField.getText().toString();
                validateToken(token);
            }
        };
        builder.setMessage(R.string.auth_copy_instructions)
                .setCancelable(false)
                .setView(view)
                .setPositiveButton(R.string.auth_accept_token, tokenAcceptListener)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        authDialog = null;
                    }
                });
        authDialog = builder.create();
        authDialog.show();
    }

    private void openTokenGenerationBrowser() {
        String url = "https://dynalist.io/developer";
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }

    private void showValidationError() {
        Toast.makeText(this, R.string.token_invalid, Toast.LENGTH_SHORT).show();
        authenticate();
    }

    private void saveToken(final String token) {
        getPreferences().edit().putString("TOKEN", token).apply();
    }

    private void validateToken(final String token) {
        JSONObject payload;
        try {
            payload = createRequestPayload(token);
        } catch (JSONException e) {
            e.printStackTrace();
            showValidationError();
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                "https://dynalist.io/api/v1/file/list",
                payload,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.getString("_code").equals("Ok")) {
                                saveToken(token);
                            } else {
                                showValidationError();
                            }
                        } catch (JSONException e) {
                            showValidationError();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        showValidationError();
                    }
                });
        queue.add(request);
    }

    private JSONObject createRequestPayload(@Nullable String token) throws JSONException {
        if (token == null) {
            token = getToken();
        }
        JSONObject payload = new JSONObject();
        payload.put("token", token);
        return payload;
    }

    private void authenticate() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.auth_instructions)
                .setCancelable(false)
                .setPositiveButton(R.string.auth_start, authStartListener);
        builder.create().show();
    }
}
