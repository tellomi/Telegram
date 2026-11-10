package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedApi;
import org.telegram.messenger.feed.FeedContract;
import org.telegram.messenger.feed.FeedController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class FeedCityActivity extends BaseFragment {

    private static final int REQ = 77;
    private final boolean explain;
    private final ArrayList<String[]> cities = new ArrayList<>();
    private RecyclerListView listView;

    public FeedCityActivity(boolean explain) {
        super();
        this.explain = explain;
    }

    @Override
    public View createView(android.content.Context context) {
        actionBar.setTitle(LocaleController.getString("Moments_SwitchCity", R.string.Moments_SwitchCity));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        if (explain) {
            TextView note = new TextView(context);
            note.setText(LocaleController.getString("Moments_Nearby_Permission", R.string.Moments_Nearby_Permission));
            note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            note.setPadding(dp(16), dp(12), dp(16), dp(8));
            root.addView(note);
            TextView allow = new TextView(context);
            allow.setText(LocaleController.getString("Moments_UseLocation", R.string.Moments_UseLocation));
            allow.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            allow.setPadding(dp(16), dp(8), dp(16), dp(8));
            allow.setOnClickListener(v -> requestLocation());
            root.addView(allow);
        }
        EditText search = new EditText(context);
        search.setHint(getString(R.string.Search));
        search.setBackground(null);
        search.setPadding(dp(16), dp(8), dp(16), dp(8));
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { query(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        root.addView(search);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter());
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        fragmentView = root;
        FeedController.getInstance(currentAccount).setAskedLocation();
        query("");
        return fragmentView;
    }

    private void requestLocation() {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(getParentActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ);
            return;
        }
        resolve();
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ) {
            boolean granted = false;
            for (int i = 0; i < grantResults.length; i++) {
                granted |= grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
            if (granted) {
                resolve();
            }
        }
    }

    private void resolve() {
        try {
            LocationManager manager = (LocationManager) ApplicationLoader.applicationContext.getSystemService(android.content.Context.LOCATION_SERVICE);
            Location location = null;
            try {
                location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            } catch (SecurityException ignore) {
            }
            if (location == null) {
                return;
            }
            Geocoder geocoder = new Geocoder(getParentActivity(), Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (list != null && !list.isEmpty()) {
                String city = list.get(0).getLocality();
                if (city == null || city.length() == 0) {
                    city = list.get(0).getAdminArea();
                }
                if (city != null) {
                    String code = (list.get(0).getCountryCode() == null ? "xx" : list.get(0).getCountryCode().toLowerCase(Locale.US)) + ":" + city.toLowerCase(Locale.US).replace(' ', '-');
                    choose(code, city);
                }
            }
        } catch (Exception ignore) {
        }
    }

    private void query(String q) {
        cities.clear();
        FeedController.getInstance(currentAccount).api.getJson("/v1/feed/cities?q=" + FeedApi.enc(q), (json, error) -> {
            ArrayList<String> codes = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            if (json != null) {
                JSONArray items = json.optJSONArray("items");
                if (items == null) {
                    items = json.optJSONArray("cities");
                }
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        codes.add(item.optString("code", item.optString("id", "")));
                        names.add(FeedContract.serverCityName(item.optString("name", ""), item.optString("local_name", ""), item.optString("label", "")));
                    }
                }
            }
            cities.clear();
            cities.addAll(FeedContract.resolveCities(codes, names, fallbackCities(), q));
            if (listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
        });
    }

    /** Used only when the cities request returns nothing usable. */
    private String[][] fallbackCities() {
        return new String[][]{
                {"cn:beijing", LocaleController.getString("Moments_City_Beijing", R.string.Moments_City_Beijing)},
                {"cn:shanghai", LocaleController.getString("Moments_City_Shanghai", R.string.Moments_City_Shanghai)},
                {"cn:guangzhou", LocaleController.getString("Moments_City_Guangzhou", R.string.Moments_City_Guangzhou)},
                {"cn:shenzhen", LocaleController.getString("Moments_City_Shenzhen", R.string.Moments_City_Shenzhen)},
                {"cn:chengdu", LocaleController.getString("Moments_City_Chengdu", R.string.Moments_City_Chengdu)},
                {"cn:hangzhou", LocaleController.getString("Moments_City_Hangzhou", R.string.Moments_City_Hangzhou)}
        };
    }

    private void choose(String code, String name) {
        FeedController.getInstance(currentAccount).setCity(code, name);
        if (getParentLayout() != null) {
            for (int i = 0; i < getParentLayout().getFragmentStack().size(); i++) {
                BaseFragment fragment = getParentLayout().getFragmentStack().get(i);
                if (fragment instanceof FeedComposerActivity) {
                    ((FeedComposerActivity) fragment).setCity(code, name);
                }
            }
        }
        finishFragment();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            text.setPadding(dp(16), dp(14), dp(16), dp(14));
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            text.setTextSize(16);
            text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            return new RecyclerListView.Holder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((TextView) holder.itemView).setText(cities.get(position)[1]);
            holder.itemView.setOnClickListener(v -> choose(cities.get(position)[0], cities.get(position)[1]));
        }

        @Override
        public int getItemCount() { return cities.size(); }
    }

    @SuppressWarnings("unused")
    private void explainDenied() {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity()).setMessage(LocaleController.getString("Moments_LocationDenied", R.string.Moments_LocationDenied)).setPositiveButton(getString(R.string.OK), null).show();
    }
}
