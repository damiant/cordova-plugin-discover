package com.ionicframework.discover;


import android.os.AsyncTask;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;


public class IonicDiscover extends CordovaPlugin {
  private static int PORT = 41234;
  private static String PREFIX = "ION_DP";
  private static String LOGTAG = "IonicDiscover";
  private volatile HashMap<String, Service> services = new HashMap<>();
  private CountDownLatch latch = null;
  private volatile boolean running = false;
  private boolean shouldRestart = false;


  /**
   * Sets the context of the Command. This can then be used to do things like
   * get file paths associated with the Activity.
   *
   * @param cordova The context of the main Activity.
   * @param cWebView The CordovaWebView Cordova is running in.
   */
  public void initialize(CordovaInterface cordova, CordovaWebView cWebView) {

  }

  /**
   * Executes the request and returns PluginResult.
   *
   * @param action            The action to execute.
   * @param args              JSONArry of arguments for the plugin.
   * @param callbackContext   The callback id used when calling back into JavaScript.
   * @return                  True if the action was valid, false if not.
   */
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (action.equals("start")) {
//      Log.d(LOGTAG, "WATCH");
      this.watch(callbackContext);
      return true;
    } else if (action.equals("stop")) {
//      Log.d(LOGTAG, "UNWATCH");
      this.unwatch(callbackContext);
      return true;
    } else if(action.equals("getServices")) {
      this.getServices(callbackContext);
      return true;
    }
    return false;
  }

  public void watch(CallbackContext callbackContext) {
    // unwatch previous connections
    this.unwatch(null);
    this.start();
  }

  public void unwatch(CallbackContext cb) {
    this.close();
    if (cb != null) {
      cb.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    }
  }

  public void getServices(CallbackContext cb) {
    if (cb != null) {
      this.gc();
      JSONObject message = generateMessage(services.values());
      cb.sendPluginResult(new PluginResult(PluginResult.Status.OK, message));
    }
  }

  private void start() {
//    Log.d(LOGTAG, "START");
    if (latch != null) return;
    latch = new CountDownLatch(1);
    running = true;

    new AsyncTask<Void, Void, JSONObject>() {
      @Override
      final protected JSONObject doInBackground(Void...params) {
        try {
          DatagramSocket socket = new DatagramSocket(PORT);
          socket.setBroadcast(true);

          byte[] buf = new byte[1024];
          DatagramPacket packet = new DatagramPacket(buf, buf.length);
          while (running) {
            socket.receive(packet);
            if (!running) continue;
//            Log.d(LOGTAG, "packet received");
            String prefix = new String(buf, 0, PREFIX.length());
            if (!prefix.equals(PREFIX)) continue;
            String result = new String(buf, PREFIX.length(), packet.getLength());
            JSONObject dict;
            try {
              dict = new JSONObject(result);
              if ("devapp".equals(dict.getString("nspace"))){
                IonicDiscover.this.addService(dict);
              }
            } catch (JSONException e) {
              Log.e(LOGTAG, "Error parsing result as JSON: " + result);
            }
          }
          socket.close();
//          Log.d(LOGTAG, "Latch unlocked by thread");
          latch.countDown();

        } catch (Exception e) {
          Log.e(LOGTAG, "Exception while listening for server broadcast");
          e.printStackTrace();
          running = false;
          latch.countDown();
        }
        return null;
      }

    }.execute();
  }

  private synchronized void addService(JSONObject dict) {
    try {
      if (!"devapp".equals(dict.getString("nspace"))) return;

      services.put(dict.getString("id"), new Service(
          dict.getString("id"),
          System.currentTimeMillis() / 1000,
          dict.getString("name"),
          dict.getString("host"),
          dict.getString("ip"),
          dict.getInt("port"),
          dict.getString("path"),
          dict.getBoolean("secure")
      ));
    } catch (ExceptionInInitializerError | JSONException e) {
      Log.e(LOGTAG, "Malformed service response");
      e.printStackTrace();
      return;
    }
    this.gc();
  }

  @Override
  public void onResume(boolean multitasking) {
    // latch may not have decremented before we went into background
    if (latch != null) {
      try {
        latch.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    if (!shouldRestart) return;
    start();
    shouldRestart = false;
  }

  @Override
  public void onPause(boolean multitasking) {
      if (running) shouldRestart = true;
      close();
  }

  private synchronized void gc() {
//    Log.d(LOGTAG, "GC");
    long expired = (System.currentTimeMillis() / 1000) - 8;
    Iterator it = this.services.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry pair = (Map.Entry)it.next();
      Service s = (Service)pair.getValue();
      if (s.timeStamp < expired) {
        it.remove();
      }
    }
  }

  private void close() {
//    Log.d(LOGTAG, "CLOSE");
    if (latch == null) return;
    running = false;
    this.cordova.getThreadPool().execute(() -> {
//      Log.d(LOGTAG, "Waiting for latch");
      try {
        latch.await();
      } catch(InterruptedException e) {
        e.printStackTrace();
      }
      services = new HashMap<>();
      latch = null;
    });

    return;
  }

  private JSONObject generateMessage(Collection<Service> services) {
    JSONObject result = new JSONObject();
    JSONArray servicesArray = new JSONArray();
    Iterator<Service> i = services.iterator();
    while(i.hasNext()) {
      Service s = i.next();
      JSONObject o = new JSONObject();
      try {
        o.put("id", s.id);
        o.put("name", s.name);
        o.put("hostname", s.hostname);
        o.put("address", s.address);
        o.put("port", s.port);
        o.put("path", s.path);
        o.put("secure", s.secure);
        servicesArray.put(o);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    try {
      result.put("services", servicesArray);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return result;
  }

  private class Service {
    public String id;
    public long timeStamp;
    public String name;
    public String hostname;
    public String address;
    public int port;
    public String path;
    public boolean secure;

    public Service(String id, long timeStamp, String name, String hostname, String address, int port, String path, boolean secure) {
      this.id = id;
      this.timeStamp = timeStamp;
      this.name = name;
      this.hostname = hostname;
      this.address = address;
      this.port = port;
      this.path = path;
      this.secure = secure;
    }
  }
}

