/*
 * Copyright (C) 2011 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.provider.Settings;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.spatial.MapHelper;
import org.odk.collect.android.utilities.InfoLogger;
import org.odk.collect.android.widgets.GeoPointWidget;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Version of the GeoPointMapActivity that uses the new Maps v2 API and Fragments to enable
 * specifying a location via placing a tracker on a map.
 *
 * @author guisalmon@gmail.com
 * @author jonnordling@gmail.com
 *
 */
public class GeoPointMapActivity extends FragmentActivity implements LocationListener, OnMarkerDragListener, OnMapLongClickListener {

	private static final String LOCATION_COUNT = "locationCount";

	private GoogleMap mMap;
	private MarkerOptions mMarkerOption;
	private Marker mMarker;
	private LatLng mLatLng;

	private TextView mLocationStatus;

	private LocationManager mLocationManager;

	private Location mLocation;
	private Button mAcceptLocation;
	private Button mReloadLocation;

	private boolean mRefreshLocation = true;
	private boolean mIsDragged = false;
	private Button mShowLocation;
	private Button mLayers;
	private boolean mGPSOn = false;
	private boolean mNetworkOn = false;

	private double mLocationAccuracy;
	private int mLocationCount = 0;

	private boolean mZoomed = false;
	private MapHelper mHelper;
//	private KmlLayer kk;

	private AlertDialog zoomDialog;
	private View zoomDialogView;

	private Button zoomPointButton;
	private Button zoomLocationButton;

	private Button clearPointButton;

	private boolean setClear = false;
	private boolean mCaptureLocation = false;
	private Boolean foundFirstLocation = false;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		if ( savedInstanceState != null ) {
			mLocationCount = savedInstanceState.getInt(LOCATION_COUNT);
		}

		try {
			setContentView(R.layout.geopoint_layout);
		} catch (NoClassDefFoundError e) {
			e.printStackTrace();
			Toast.makeText(getBaseContext(), getString(R.string.google_play_services_error_occured),
					Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
		if ( mMap == null ) {
			Toast.makeText(getBaseContext(), getString(R.string.google_play_services_error_occured),
					Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		mMap.setMyLocationEnabled(true);
		mMap.getUiSettings().setCompassEnabled(true);
		mMap.getUiSettings().setMyLocationButtonEnabled(false);
		mMap.getUiSettings().setZoomControlsEnabled(false);
		mMap.setOnMarkerDragListener(this);
		mMap.setOnMapLongClickListener(this);
		mMarkerOption = new MarkerOptions();
		mHelper = new MapHelper(this,mMap);
		mHelper.setBasemap();



		mLocationAccuracy = GeoPointWidget.DEFAULT_LOCATION_ACCURACY;


		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mLocationStatus = (TextView) findViewById(R.id.location_status);



		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);



		mAcceptLocation = (Button) findViewById(R.id.accept_location);

			mAcceptLocation.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Collect.getInstance().getActivityLogger().logInstanceAction(this, "acceptLocation", "OK");
					returnLocation();
				}
			});


		mReloadLocation = (Button) findViewById(R.id.reload_location);
		mReloadLocation.setEnabled(false);
		mReloadLocation.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mMarker != null){
					mMarker.remove();
				}

				mLatLng = null;
				mMarker = null;

				setClear = false;
				mLatLng = new LatLng(mLocation.getLatitude(), mLocation.getLongitude());
				mMarkerOption.position(mLatLng);
				if (mMarker == null){
					mMarker = mMap.addMarker(mMarkerOption);
				}

//				mMarker.setPosition(mLatLng);
				mCaptureLocation = true;
				mIsDragged = false;
				zoomToPoint();
			}

		});


		// Focuses on marked location
		mShowLocation = ((Button) findViewById(R.id.show_location));
//		mShowLocation.setClickable(false);
		mShowLocation.setEnabled(false);
		mShowLocation.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showZoomDialog();
			}
		});


		// Menu Layer Toggle
		mLayers = ((Button) findViewById(R.id.layer_menu));
		mLayers.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mHelper.showLayersDialog();
			}
		});
		zoomDialogView = getLayoutInflater().inflate(R.layout.geopoint_zoom_dialog, null);
		zoomLocationButton = (Button) zoomDialogView.findViewById(R.id.zoom_location);
		zoomLocationButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				zoomToLocation();
				zoomDialog.dismiss();
			}
		});

		zoomPointButton = (Button) zoomDialogView.findViewById(R.id.zoom_point);
		zoomPointButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				zoomToPoint();
				zoomDialog.dismiss();
			}
		});

		clearPointButton = (Button) findViewById(R.id.clear);
		clearPointButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mMarker!=null){
					mMarker.remove();
				}

				mLatLng = null;
				mMarker = null;
				setClear = true;
				mIsDragged = false;
				mCaptureLocation = false;
			}
		});

		Intent intent = getIntent();
		if (intent != null && intent.getExtras() != null) {
			if ( intent.hasExtra(GeoPointWidget.LOCATION) ) {
				double[] location = intent.getDoubleArrayExtra(GeoPointWidget.LOCATION);
				mLatLng = new LatLng(location[0], location[1]);
//				mMarkerOption.position(mLatLng);
//				mMarker = mMap.addMarker(mMarkerOption);
				mCaptureLocation = true;
				mIsDragged = true;
				mReloadLocation.setEnabled(false);

			}
			if ( intent.hasExtra(GeoPointWidget.ACCURACY_THRESHOLD) ) {
				mLocationAccuracy = intent.getDoubleExtra(GeoPointWidget.ACCURACY_THRESHOLD, GeoPointWidget.DEFAULT_LOCATION_ACCURACY);
			}

		}

		/*Zoom only if there's a previous location*/
		if (mLatLng != null){
			mLocationStatus.setVisibility(View.GONE);
			mShowLocation.setEnabled(true);
			mMarkerOption.position(mLatLng);
			mMarker = mMap.addMarker(mMarkerOption);
			mCaptureLocation = true;
			foundFirstLocation = true;

			mZoomed = true;
			zoomToPoint();
		}


	}

	private void stopGeolocating() {
//		mRefreshLocation = false;
//		mReloadLocation.setEnabled(true);
//		mLocationManager.removeUpdates(this);
//		mMarker.setDraggable(true);
//		mLocationStatus.setVisibility(View.GONE);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Collect.getInstance().getActivityLogger().logOnStart(this);
	}

	@Override
	protected void onStop() {
		Collect.getInstance().getActivityLogger().logOnStop(this);
		super.onStop();
	}


	private void returnLocation() {
		Intent i = new Intent();
		if(setClear){
			i.putExtra(FormEntryActivity.LOCATION_RESULT, "");
			setResult(RESULT_OK, i);

		}else if (mIsDragged){
			Log.i(getClass().getName(), "IsDragged !!!");
			i.putExtra(
					FormEntryActivity.LOCATION_RESULT,
					mLatLng.latitude + " " + mLatLng.longitude + " "
							+ 0 + " " + 0);
			setResult(RESULT_OK, i);
		} else if (mLocation != null) {
			Log.i(getClass().getName(), "IsNotDragged !!!");

			i.putExtra(
					FormEntryActivity.LOCATION_RESULT,
					mLocation.getLatitude() + " " + mLocation.getLongitude() + " "
							+ mLocation.getAltitude() + " " + mLocation.getAccuracy());
			setResult(RESULT_OK, i);
		}
		finish();
	}


	private String truncateFloat(float f) {
		return new DecimalFormat("#.##").format(f);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mLocationManager.removeUpdates(this);
	}


	@Override
	protected void onResume() {
		super.onResume();
		upMyLocationOverlayLayers();
//		if ( mMap == null ) {
//			mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
//			mHelper = new MapHelper(this,mMap);
//			mHelper.setBasemap();

//			if ( mMap == null ) {
//				Toast.makeText(getBaseContext(), getString(R.string.google_play_services_error_occured),
//						Toast.LENGTH_SHORT).show();
//				finish();
//				return;
//			}

			// possibly enable clear value action...
//			if (mCaptureLocation) {
//				mMap.setOnMarkerDragListener(this);
//				mMap.setOnMapLongClickListener(this);
//			}

			/*Zoom only if there's a previous location*/
//			if (mLatLng != null){
//				mMarkerOption.position(mLatLng);
//				mMarker = mMap.addMarker(mMarkerOption);
//				mMarker.setDraggable(mCaptureLocation);
//				mZoomed = true;
//				mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLatLng, 16));
//			}
//
//			mShowLocation.setClickable(mMarker != null);
//		}

//		upMyLocationOverlayLayers();
	}


	private void upMyLocationOverlayLayers(){
		// make sure we have a good location provider before continuing
//		if (mCaptureLocation) {
//			mMap.setOnMarkerDragListener(this);
//			mMap.setOnMapLongClickListener(this);
//		}
		List<String> providers = mLocationManager.getProviders(true);
		for (String provider : providers) {
			if (provider.equalsIgnoreCase(LocationManager.GPS_PROVIDER)) {
				mGPSOn = true;
				mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
			}
			if (provider.equalsIgnoreCase(LocationManager.NETWORK_PROVIDER)) {
				mNetworkOn = true;
				mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
			}
		}
//		mShowLocation.setClickable(mMarker != null);
		if (!mGPSOn && !mNetworkOn) {
			showGPSDisabledAlertToUser();
		}else{
			overlayMyLocationLayers();
		}
	}
	private void overlayMyLocationLayers(){
		if(mLatLng != null){
//			mMap.getOverlays().add(mMarker);
			//mMarker = mMap.addMarker(mMarkerOption);
		}
		mMap.setOnMarkerDragListener(this);
		mMap.setOnMapLongClickListener(this);


	}


	@Override
	public void onLocationChanged(Location location) {
		mLocation = location;
		mReloadLocation.setEnabled(true);
		mShowLocation.setEnabled(true);


		if (mLocation != null) {
			mLocationStatus.setText(getString(R.string.location_provider_accuracy, mLocation.getProvider(), truncateFloat(mLocation.getAccuracy())));
			if (!mCaptureLocation & !setClear){
				mLatLng = new LatLng(mLocation.getLatitude(), mLocation.getLongitude());
				mMarkerOption.position(mLatLng);
				mMarker = mMap.addMarker(mMarkerOption);
//				mMarker.setPosition(mLatLng);
				mCaptureLocation = true;

			}
			if(!foundFirstLocation){
//				showZoomDialog();
				zoomToPoint();
				foundFirstLocation = true;

			}

		} else {
			InfoLogger.geolog("GeoPointMapActivity: " + System.currentTimeMillis() +
					" onLocationChanged(" + mLocationCount + ") null location");
		}

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

	@Override
	public void onMarkerDrag(Marker arg0) {

	}

	@Override
	public void onMarkerDragEnd(Marker marker) {
		mLatLng = marker.getPosition();
		mIsDragged = true;
		mCaptureLocation = true;
		setClear = false;
		mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mLatLng, mMap.getCameraPosition().zoom));

	}

	@Override
	public void onMarkerDragStart(Marker arg0) {
//		stopGeolocating();
	}

	@Override
	public void onMapLongClick(LatLng latLng) {
		mLatLng=latLng;
		if (mMarker == null) {
			mMarkerOption.position(latLng);
			mMarker = mMap.addMarker(mMarkerOption);
//			mShowLocation.setClickable(true);
		} else {
			mMarker.setPosition(latLng);
		}
		mShowLocation.setEnabled(true);
		mMarker.setDraggable(true);
		mIsDragged = true;
		setClear = false;
		mCaptureLocation = true;




	}

	private void zoomToLocation() {
		LatLng here = new LatLng(mLocation.getLatitude(), mLocation.getLongitude());
		if(mLocation != null) {
			mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(here, 16));
		}
	}

	private void zoomToPoint(){
		if(mLatLng != null) {
			mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMarker.getPosition(), 16));
		}

	}

	public void showZoomDialog() {


		if (zoomDialog == null) {
			AlertDialog.Builder p_builder = new AlertDialog.Builder(this);
			p_builder.setTitle(getString(R.string.zoom_to_where));
			p_builder.setView(zoomDialogView)
					.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					})
					.setOnCancelListener(new DialogInterface.OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							dialog.cancel();
							zoomDialog.dismiss();
						}
					});
			zoomDialog = p_builder.create();
		}
		//If feature enable zoom to button else disable
		if (mLocation!= null){
			zoomLocationButton.setEnabled(true);
			zoomLocationButton.setBackgroundColor(Color.parseColor("#50cccccc"));
			zoomLocationButton.setTextColor(Color.parseColor("#ff333333"));
		}else{
			zoomLocationButton.setEnabled(false);
			zoomLocationButton.setBackgroundColor(Color.parseColor("#50e2e2e2"));
			zoomLocationButton.setTextColor(Color.parseColor("#FF979797"));
		}

		if (mLatLng != null & !setClear) {
			zoomPointButton.setEnabled(true);
			zoomPointButton.setBackgroundColor(Color.parseColor("#50cccccc"));
			zoomPointButton.setTextColor(Color.parseColor("#ff333333"));
		}else{
			zoomPointButton.setEnabled(false);
			zoomPointButton.setBackgroundColor(Color.parseColor("#50e2e2e2"));
			zoomPointButton.setTextColor(Color.parseColor("#FF979797"));
		}
		zoomDialog.show();

	}
	private void showGPSDisabledAlertToUser(){
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setMessage(getString(R.string.gps_enable_message))
				.setCancelable(false)
				.setPositiveButton(getString(R.string.enable_gps),
						new DialogInterface.OnClickListener(){
							public void onClick(DialogInterface dialog, int id){
								startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 0);
							}
						});
		alertDialogBuilder.setNegativeButton(getString(R.string.cancel),
				new DialogInterface.OnClickListener(){
					public void onClick(DialogInterface dialog, int id){
						dialog.cancel();
					}
				});
		AlertDialog alert = alertDialogBuilder.create();
		alert.show();
	}

}