package io.left.hellomesh;

import android.app.Activity;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.left.rightmesh.android.AndroidMeshManager;
import io.left.rightmesh.android.MeshService;
import io.left.rightmesh.id.MeshID;
import io.left.rightmesh.mesh.MeshManager;
import io.left.rightmesh.mesh.MeshStateListener;
import io.left.rightmesh.util.MeshUtility;
import io.left.rightmesh.util.RightMeshException;
import io.reactivex.functions.Consumer;

import static android.icu.lang.UCharacter.isLetterOrDigit;
import static io.left.rightmesh.mesh.MeshManager.DATA_RECEIVED;
import static io.left.rightmesh.mesh.MeshManager.PEER_CHANGED;
import static io.left.rightmesh.mesh.MeshManager.REMOVED;

public class MainActivity extends Activity implements MeshStateListener {
    // Port to bind app to.
    private static final int QUESTIONNAIRE_PORT = 9876;

    // MeshManager instance - interface to the mesh network.
    AndroidMeshManager mm = null;

    // Set to keep track of peers connected to the mesh.
    HashSet<MeshID> users = new HashSet<>();

    /**
     * Called when app first opens, initializes {@link AndroidMeshManager} reference (which will
     * start the {@link MeshService} if it isn't already running.
     *
     * @param savedInstanceState passed from operating system
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mm = AndroidMeshManager.getInstance(MainActivity.this, MainActivity.this);
        Button btnConfigure = (Button) findViewById(R.id.btnConfigure);
        btnConfigure.setEnabled(true);
    }

    /**
     * Called when activity is on screen.
     */
    @Override
    protected void onResume() {
        try {
            super.onResume();
            mm.resume();
        } catch (MeshService.ServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when the app is being closed (not just navigated away from). Shuts down
     * the {@link AndroidMeshManager} instance.
     */
    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
            mm.stop();
        } catch (MeshService.ServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by the {@link MeshService} when the mesh state changes. Initializes mesh connection
     * on first call.
     *
     * @param uuid our own user id on first detecting
     * @param state state which indicates SUCCESS or an error code
     */
    @Override
    public void meshStateChanged(MeshID uuid, int state) {
        if (state == MeshStateListener.SUCCESS) {
            try {
                // Binds this app to MESH_PORT.
                // This app will now receive all events generated on that port.
                mm.bind(QUESTIONNAIRE_PORT);

                // Subscribes handlers to receive events from the mesh.
                mm.on(DATA_RECEIVED, new Consumer() {
                    @Override
                    public void accept(Object o) throws Exception {
                        handleDataReceived((MeshManager.RightMeshEvent) o);
                    }
                });
                mm.on(PEER_CHANGED, new Consumer() {
                    @Override
                    public void accept(Object o) throws Exception {
                        handlePeerChanged((MeshManager.RightMeshEvent) o);
                    }
                });

                // If you are using Java 8 or a lambda backport like RetroLambda, you can use
                // a more concise syntax, like the following:
                // mm.on(PEER_CHANGED, this::handlePeerChanged);
                // mm.on(DATA_RECEIVED, this::dataReceived);

                // Enable buttons now that mesh is connected.
                Button btnConfigure = (Button) findViewById(R.id.btnConfigure);
                btnConfigure.setEnabled(true);
            } catch (RightMeshException e) {
                String status = "Error initializing the library" + e.toString();
                Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show();
                TextView txtStatus = (TextView) findViewById(R.id.txtStatus);
                txtStatus.setText(status);
                return;
            }
        }

        // Update display on successful calls (i.e. not FAILURE or DISABLED).
        if (state == MeshStateListener.SUCCESS || state == MeshStateListener.RESUME) {
            updateStatus();
        }
    }

    /**
     * Update the {@link TextView} with a list of all peers.
     */
    private void updateStatus() {
        String status = "uuid: " + mm.getUuid().toString() + "\npeers:\n";
        for (MeshID user : users) {
            status += user.toString() + "\n";
        }

        TextView txtStatus = (TextView) findViewById(R.id.txtStatus);
        if (txtStatus != null) {
            txtStatus.setText(status);
        }
    }


    /**
     * Handles incoming data events from the mesh - toasts the contents of the data.
     *
     * @param e event object from mesh
     */
    private void handleDataReceived(MeshManager.RightMeshEvent e) {
        final MeshManager.DataReceivedEvent event = (MeshManager.DataReceivedEvent) e;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Toast data contents.
                RelativeLayout layout = (RelativeLayout)findViewById(R.id.layout);
                layout.removeAllViews();
                TextView txtStatus = new TextView(MainActivity.this);
                txtStatus.setText("");
                layout.addView(txtStatus);
                String message = new String(event.data);
                String[] parsedArr = parseEncString(message);
                //Toast.makeText(MainActivity.this, "parsed string", Toast.LENGTH_SHORT).show();
                /*for(int i=0;i < parsedArr.length;i++){
                    Toast.makeText(MainActivity.this, parsedArr[i], Toast.LENGTH_SHORT).show();
                }
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();*/

                // Play a notification.
                generateForm(parsedArr);
                //Toast.makeText(MainActivity.this, "generated form", Toast.LENGTH_SHORT).show();
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(MainActivity.this, notification);
                r.play();
            }
        });
    }

    /**
     * Handles peer update events from the mesh - maintains a list of peers and updates the display.
     *
     * @param e event object from mesh
     */
    private void handlePeerChanged(MeshManager.RightMeshEvent e) {
        // Update peer list.
        MeshManager.PeerChangedEvent event = (MeshManager.PeerChangedEvent) e;
        if (event.state != REMOVED && !users.contains(event.peerUuid)) {
            users.add(event.peerUuid);
            Button btnConfigure = (Button) findViewById(R.id.btnConfigure);
            btnConfigure.setVisibility(View.GONE);
        } else if (event.state == REMOVED){
            users.remove(event.peerUuid);
            Button btnConfigure = (Button) findViewById(R.id.btnConfigure);
            btnConfigure.setVisibility(View.VISIBLE);
        }

        // Update display.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateStatus();
            }
        });
    }

    /**
     * Sends "hello" to all known peers.
     *
     * @param v calling view
     */
    public void sendAnswers(View v, String str) throws RightMeshException {
        for(MeshID receiver : users) {
            String msg = str;
            MeshUtility.Log(this.getClass().getCanonicalName(), msg);
            byte[] testData = msg.getBytes();
            mm.sendDataReliable(receiver, QUESTIONNAIRE_PORT, testData);
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Open mesh settings screen.
     *
     * @param v calling view
     */
    public void configure(View v)
    {
        try {
            mm.showSettingsActivity();
        } catch(RightMeshException ex) {
            MeshUtility.Log(this.getClass().getCanonicalName(), "Service not connected");
        }
    }

    // Parsing encoded string
    public String[] parseEncString(String Form)
    {
        int count = 0;
        String c[]= new String[Form.length()];
        for(int i=0;i < Form.length();i++) {
            String check = "" + Form.charAt(i);
            if(check != ",") {
                c[count] = check;
                count++;
            }else if(isLetterOrDigit(Form.charAt(i))) {
                String id = Form.substring(i,-1);
                c[count] = id;
                count += Form.length() - i ;
                break;
            }
        }
        String genform[]= new String[count];
        for(int k =0; k < count; k++){
            genform[k] = c[k];
        }
        //Toast.makeText(MainActivity.this, "created genform", Toast.LENGTH_SHORT).show();
        return genform;

    }

    // looks through the parsed string array and creates the form
    public void generateForm(String[] genform) {
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout);
        int prevTextViewId = 0;
        int curTextViewId = 0;
        TextView textView = new TextView(this);
        EditText editText = new EditText(this);
        final ArrayList<EditText> answers = new ArrayList<EditText>();
        final ArrayList<Spinner> spins = new ArrayList<Spinner>();
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        for (int i = 0; i < genform.length; i++) {
            String item = genform[i];
            switch (item) {
                case "?": //What is your Name?
                    textView = new TextView(this);
                    textView.setText("What is your name?");
                    curTextViewId = prevTextViewId + 1;
                    textView.setTag("test"+ curTextViewId);
                    params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    if (prevTextViewId == 0) {
                        params.addRule(RelativeLayout.BELOW, R.id.txtStatus);
                    } else {
                        params.addRule(RelativeLayout.BELOW, prevTextViewId);
                    }
                    textView.setLayoutParams(params);
                    prevTextViewId = curTextViewId;
                    layout.addView(textView, params);
                    editText = new EditText(this);
                    curTextViewId = prevTextViewId + 1;
                    editText.setId(curTextViewId);
                    params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.BELOW, prevTextViewId);
                    editText.setLayoutParams(params);
                    prevTextViewId = curTextViewId;
                    layout.addView(editText, params);
                    answers.add(editText);
                    break;
                case "*": //What is your Age?
                    textView = new TextView(this);
                    textView.setText("How old are you?");
                    curTextViewId = prevTextViewId + 1;
                    textView.setId(curTextViewId);
                    params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    if (prevTextViewId == 0) {
                        params.addRule(RelativeLayout.BELOW, R.id.txtStatus);
                    } else {
                        params.addRule(RelativeLayout.BELOW, prevTextViewId);
                    }
                    textView.setLayoutParams(params);
                    prevTextViewId = curTextViewId;
                    layout.addView(textView, params);
                    editText = new EditText(this);
                    curTextViewId = prevTextViewId + 1;
                    editText.setId(curTextViewId);
                    params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.BELOW, prevTextViewId);
                    editText.setLayoutParams(params);
                    prevTextViewId = curTextViewId;
                    layout.addView(editText, params);
                    answers.add(editText);
                    break;
                case "+": //What is your Gender?
                    textView = new TextView(this);
                    textView.setText("What is your gender?");
                    curTextViewId = prevTextViewId + 1;
                    textView.setId(curTextViewId);
                    params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    if (prevTextViewId == 0) {
                        params.addRule(RelativeLayout.BELOW, R.id.txtStatus);
                    } else {
                        params.addRule(RelativeLayout.BELOW, prevTextViewId);
                    }
                    textView.setLayoutParams(params);
                    prevTextViewId = curTextViewId;
                    layout.addView(textView, params);
                    editText = new EditText(this);
                    curTextViewId = prevTextViewId + 1;
                    editText.setId(curTextViewId);
                    params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.BELOW, prevTextViewId);
                    editText.setLayoutParams(params);
                    prevTextViewId = curTextViewId;
                    layout.addView(editText, params);
                    answers.add(editText);
                    break;
                case "-"://What is your School ID?
                    textView = new TextView(this);
                    textView.setText("What is your school ID?");
                    curTextViewId = prevTextViewId + 1;
                    textView.setId(curTextViewId);
                    params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    if (prevTextViewId == 0) {
                        params.addRule(RelativeLayout.BELOW, R.id.txtStatus);
                    } else {
                        params.addRule(RelativeLayout.BELOW, prevTextViewId);
                    }
                    textView.setLayoutParams(params);
                    prevTextViewId = curTextViewId;
                    layout.addView(textView, params);
                    editText = new EditText(this);
                    curTextViewId = prevTextViewId + 1;
                    editText.setId(curTextViewId);
                    params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                    params.addRule(RelativeLayout.BELOW, prevTextViewId);
                    editText.setLayoutParams(params);
                    prevTextViewId = curTextViewId;
                    layout.addView(editText, params);
                    answers.add(editText);
                    break;
                default:
                    idLookUp(genform[i]);
            }

        }
        textView = new TextView(this);
        textView.setText("Over the last two weeks, how often have you been bothered by any of the following problems?");
        while(curTextViewId < 9) {
            curTextViewId = curTextViewId + 1;
        }
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        if (prevTextViewId == 0) {
            params.addRule(RelativeLayout.BELOW, R.id.txtStatus);
        } else {
            params.addRule(RelativeLayout.BELOW, prevTextViewId);
        }
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        textView = new TextView(this);
        textView.setText("Little interest or pleasure in doing things?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        List<String> spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        textView = new TextView(this);
        textView.setText("Feeling down, depressed, or hopeless?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        sp = new Spinner(this);
        spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        textView = new TextView(this);
        textView.setText("Trouble falling or staying asleep, or sleeping too much?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        sp = new Spinner(this);
        spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        textView = new TextView(this);
        textView.setText("Feeling tired or having little energy?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        sp = new Spinner(this);
        spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        textView = new TextView(this);
        textView.setText("Poor appetite or overeating?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        sp = new Spinner(this);
        spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        textView = new TextView(this);
        textView.setText("Feeling bad about yourself - or that you are a failure or have let yourself or your family down?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        sp = new Spinner(this);
        spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        textView = new TextView(this);
        textView.setText("Trouble concentrating on things, such as reading the newspaper or watching television?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        sp = new Spinner(this);
        spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        textView = new TextView(this);
        textView.setText("Moving or speaking so slowly that other people could have noticed?\n" + "Or the opposite - being so fidgety or restless that you have been moving around a lot more than usual?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        sp = new Spinner(this);
        spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        textView = new TextView(this);
        textView.setText("Thoughts that you would be better off dead, or of hurting yourself in some way?");
        curTextViewId = prevTextViewId + 1;
        textView.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        textView.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        layout.addView(textView, params);
        spinnerArray = new ArrayList<String>();
        spinnerArray.add("Not at all");
        spinnerArray.add("Several days");
        spinnerArray.add("More than half the days");
        spinnerArray.add("Nearly every day");
        sp = new Spinner(this);
        spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
        sp.setAdapter(spinnerArrayAdapter);
        curTextViewId = prevTextViewId + 1;
        sp.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        sp.setLayoutParams(params);
        prevTextViewId = curTextViewId;
        spins.add(sp);
        layout.addView(sp, params);
        Button submit = new Button(this);
        submit.setText("Submit");
        curTextViewId = prevTextViewId + 1;
        submit.setId(curTextViewId);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, prevTextViewId);
        prevTextViewId = curTextViewId;
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                try {
                    String str = "";
                    for(EditText editText : answers) {
                        if(str == ""){
                            str = str + editText.getText().toString();
                        }
                        else
                            str = str + ", " + editText.getText().toString();
                    }
                    str = str + ", " + 0;
                    int score = 0;
                    for(int i = 0; i < spins.size(); i++) {
                        score = score + spins.get(i).getSelectedItemPosition();
                    }
                    str = str + ", " + score;
                    sendAnswers(v, str);
                } catch (RightMeshException e) {
                    e.printStackTrace();
                }
            }
        });
        layout.addView(submit, params);
    }


    // takes the last id of the parsed string and does a lookup to create the questionnaire form
    public void idLookUp(String formid){
        int x = 0;
        if (formid == "1")
            x =1;
            //Generate Depression Questionnaire
        else
            x =2;
            //Wrong ID

    }
}



