package com.sinhvien.appcalories;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.sinhvien.appcalories.models.Food;
import com.sinhvien.appcalories.models.SelectedFood;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.graphics.PorterDuff;
import androidx.core.content.ContextCompat;
import android.widget.ProgressBar;

public class HomeActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "HomeActivity";

    // UI Components
    private TextView txtBMI, txtGoalToday, txtCalTotal;
    private Button btnCreateProfile;
    private CardView cardBMIInfo;
    private DrawerLayout drawerLayout;
    private LinearLayout[] mealContainers;
    private Button[] btnChonMeals;
    private ProgressBar progressCalories;
    private TextView txtCaloriesConsumed, txtCaloriesLeft, txtPercentage;
    private int totalCaloriesGoal = 2000; // Có thể lấy từ Firebase

    // Firebase
    private DatabaseReference databaseReference;
    private FirebaseAuth firebaseAuth;

    // Data
    private int totalCalories = 0;
    private int caloriesTarget = 0;
    private Map<String, List<SelectedFood>> selectedFoodsMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        initViews();
        setupNavigation();
        setupMealSelection();

        selectedFoodsMap = new HashMap<>();
        String[] categories = {"Bữa sáng", "Bữa trưa", "Bữa chiều", "Bữa tối"};
        for (String category : categories) {
            selectedFoodsMap.put(category, new ArrayList<>());
        }

        firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            checkUserProfile(user.getUid());
        }
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        txtBMI = findViewById(R.id.txt_bmi);
        txtGoalToday = findViewById(R.id.txt_goal_today);
        txtCalTotal = findViewById(R.id.txtCalTotal);
        cardBMIInfo = findViewById(R.id.card_bmi_info);
        btnCreateProfile = findViewById(R.id.btn_create_profile);
        btnCreateProfile.setVisibility(View.GONE);

        // Initialize meal containers and buttons
        int[] containerIds = {R.id.container_sang, R.id.container_trua, R.id.container_chieu, R.id.container_toi};
        int[] btnChonIds = {R.id.btnChonSang, R.id.btnChonTrua, R.id.btnChonChieu, R.id.btnChonToi};

        mealContainers = new LinearLayout[4];
        btnChonMeals = new Button[4];

        for (int i = 0; i < 4; i++) {
            mealContainers[i] = findViewById(containerIds[i]);
            btnChonMeals[i] = findViewById(btnChonIds[i]);
        }
        progressCalories = findViewById(R.id.progress_calories);
        txtCaloriesConsumed = findViewById(R.id.txt_calories_consumed);
        txtCaloriesLeft = findViewById(R.id.txt_calories_left);
        txtPercentage = findViewById(R.id.txt_percentage);
    }
    private void updateCaloriesProgress(int consumedCalories) {
        // Tính toán giá trị
        int remaining = totalCaloriesGoal - consumedCalories;
        int progress = (int) (((float)consumedCalories / totalCaloriesGoal) * 100);

        // Cập nhật ProgressBar
        progressCalories.setProgress(progress);

        // Đổi màu nếu vượt quá 100%
        if (progress > 100) {
            progressCalories.getProgressDrawable().setColorFilter(
                    ContextCompat.getColor(this, R.color.warning_red),
                    PorterDuff.Mode.SRC_IN
            );
        } else {
            progressCalories.getProgressDrawable().setColorFilter(
                    ContextCompat.getColor(this, R.color.progress_foreground),
                    PorterDuff.Mode.SRC_IN
            );
        }

        // Cập nhật text
        txtCaloriesConsumed.setText("Đã ăn: " + consumedCalories + " kcal");
        txtCaloriesLeft.setText("Còn lại: " + remaining + " kcal");
        txtPercentage.setText(progress + "%");
        txtCalTotal.setText(consumedCalories + "/" + totalCaloriesGoal + " kcal");
    }

    private void setupNavigation() {
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, findViewById(R.id.toolbar),
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
    }

    private void setupMealSelection() {
        btnCreateProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        final String[] categories = {"Bữa sáng", "Bữa trưa", "Bữa chiều", "Bữa tối"};

        for (int i = 0; i < 4; i++) {
            int finalI = i;
            btnChonMeals[i].setOnClickListener(v ->
                    showFoodSelectionDialog(categories[finalI]));
        }
    }

    private void showFoodSelectionDialog(String category) {
        DatabaseReference foodRef = FirebaseDatabase.getInstance().getReference("Foods");
        Query query = foodRef.orderByChild("category").equalTo(category);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Food> foodList = new ArrayList<>();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    try {
                        Food food = dataSnapshot.getValue(Food.class);
                        if (food != null) {
                            // Ensure ID is set (for manual entries)
                            if (food.getId() == null) {
                                food.setId(dataSnapshot.getKey());
                            }
                            foodList.add(food);
                        }
                    } catch (DatabaseException e) {
                        Log.e(TAG, "Error parsing food data", e);
                    }
                }

                if (foodList.isEmpty()) {
                    Toast.makeText(HomeActivity.this,
                            "Không có món ăn nào trong danh mục " + category,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                showMultiSelectFoodDialog(foodList, category);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HomeActivity.this,
                        "Lỗi khi tải dữ liệu: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Database error: " + error.getMessage());
            }
        });
    }

    private void showMultiSelectFoodDialog(List<Food> foodList, String category) {
        boolean[] checkedItems = new boolean[foodList.size()];
        String[] foodNames = new String[foodList.size()];

        for (int i = 0; i < foodList.size(); i++) {
            Food food = foodList.get(i);
            foodNames[i] = String.format("%s (%d cal/100g)", food.getTenMon(), food.getCalories());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn món ăn cho " + category)
                .setMultiChoiceItems(foodNames, checkedItems, (dialog, which, isChecked) -> {
                    // Handle item selection
                })
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    // Get selected foods
                    AlertDialog alertDialog = (AlertDialog) dialog;
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            showQuantityDialog(foodList.get(i), category);
                        }
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
    private void showQuantityDialog(Food food, String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_food_quantity, null);

        TextView txtFoodName = dialogView.findViewById(R.id.txt_food_name);
        TextView txtCalPer100g = dialogView.findViewById(R.id.txt_cal_per_100g);
        EditText edtQuantity = dialogView.findViewById(R.id.edt_quantity);

        txtFoodName.setText(food.getTenMon());
        txtCalPer100g.setText(String.format("%d cal/100g", food.getCalories()));
        edtQuantity.setText("100"); // Default quantity

        builder.setView(dialogView)
                .setTitle("Nhập khối lượng")
                .setPositiveButton("Thêm", (dialog, which) -> {
                    try {
                        int quantity = Integer.parseInt(edtQuantity.getText().toString());
                        if (quantity <= 0) {
                            Toast.makeText(this, "Khối lượng phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        addFoodToMeal(food, category, quantity);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Vui lòng nhập số hợp lệ", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void addFoodToMeal(Food food, String category, int quantity) {
        SelectedFood selectedFood = new SelectedFood(food, quantity);
        selectedFoodsMap.get(category).add(selectedFood);
        updateMealUI(category);
        calculateTotalCalories();
    }

    private void updateMealUI(String category) {
        int containerIndex = getContainerIndex(category);
        if (containerIndex == -1) return;

        LinearLayout container = mealContainers[containerIndex];
        container.removeAllViews();

        List<SelectedFood> selectedFoods = selectedFoodsMap.get(category);
        int mealCalories = 0;

        for (SelectedFood selectedFood : selectedFoods) {
            View foodItemView = LayoutInflater.from(this).inflate(R.layout.item_selected_food, container, false);
            TextView txtFoodName = foodItemView.findViewById(R.id.txt_food_name);
            TextView txtQuantity = foodItemView.findViewById(R.id.txt_quantity);
            TextView txtTotalCal = foodItemView.findViewById(R.id.txt_total_cal);
            Button btnEdit = foodItemView.findViewById(R.id.btn_edit);
            Button btnRemove = foodItemView.findViewById(R.id.btn_remove);

            int calories = (selectedFood.getFood().getCalories() * selectedFood.getQuantity()) / 100;
            mealCalories += calories;

            txtFoodName.setText(selectedFood.getFood().getTenMon());
            txtQuantity.setText(String.format("%dg", selectedFood.getQuantity()));
            txtTotalCal.setText(String.format("%d cal", calories));
            btnEdit.setOnClickListener(v -> showEditDialog(selectedFood, category));
            btnRemove.setOnClickListener(v -> removeFood(selectedFood, category));

            container.addView(foodItemView);
        }

        // Update meal summary
        TextView txtCalMeal = getMealCalorieTextView(containerIndex);
        if (txtCalMeal != null) {
            txtCalMeal.setText(String.format("%d cal", mealCalories));
        }
    }

    private void showEditDialog(SelectedFood selectedFood, String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_food_quantity, null);

        TextView txtFoodName = dialogView.findViewById(R.id.txt_food_name);
        TextView txtCalPer100g = dialogView.findViewById(R.id.txt_cal_per_100g);
        EditText edtQuantity = dialogView.findViewById(R.id.edt_quantity);

        txtFoodName.setText(selectedFood.getFood().getTenMon());
        txtCalPer100g.setText(String.format("%d cal/100g", selectedFood.getFood().getCalories()));
        edtQuantity.setText(String.valueOf(selectedFood.getQuantity()));

        builder.setView(dialogView)
                .setTitle("Chỉnh sửa khối lượng")
                .setPositiveButton("Lưu", (dialog, which) -> {
                    try {
                        int newQuantity = Integer.parseInt(edtQuantity.getText().toString());
                        if (newQuantity <= 0) {
                            Toast.makeText(this, "Khối lượng phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectedFood.setQuantity(newQuantity);
                        updateMealUI(category);
                        calculateTotalCalories();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Vui lòng nhập số hợp lệ", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void removeFood(SelectedFood selectedFood, String category) {
        selectedFoodsMap.get(category).remove(selectedFood);
        updateMealUI(category);
        calculateTotalCalories();
    }

    private int getContainerIndex(String category) {
        switch (category) {
            case "Bữa sáng": return 0;
            case "Bữa trưa": return 1;
            case "Bữa chiều": return 2;
            case "Bữa tối": return 3;
            default: return -1;
        }
    }

    private TextView getMealCalorieTextView(int containerIndex) {
        switch (containerIndex) {
            case 0: return findViewById(R.id.txtCalSang);
            case 1: return findViewById(R.id.txtCalTrua);
            case 2: return findViewById(R.id.txtCalChieu);
            case 3: return findViewById(R.id.txtCalToi);
            default: return null;
        }
    }

    private void calculateTotalCalories() {
        totalCalories = 0;
        for (List<SelectedFood> foodList : selectedFoodsMap.values()) {
            for (SelectedFood selectedFood : foodList) {
                totalCalories += (selectedFood.getFood().getCalories() * selectedFood.getQuantity()) / 100;
            }
        }

        // Hiển thị tổng calories + mục tiêu
        txtCalTotal.setText(String.format("Tổng Calories: %d/%d kcal", totalCalories, caloriesTarget));

        // Đổi màu chữ dựa trên mục tiêu
        if (totalCalories > caloriesTarget) {
            txtCalTotal.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else if (totalCalories == caloriesTarget) {
            txtCalTotal.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            txtCalTotal.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        }

        // Cập nhật ProgressBar
        updateCaloriesProgress(totalCalories);
    }

    private void checkUserProfile(String userId) {
        databaseReference = FirebaseDatabase.getInstance().getReference("UserProfiles").child(userId);
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    findViewById(R.id.card_setup_profile).setVisibility(View.VISIBLE);
                    btnCreateProfile.setVisibility(View.VISIBLE);
                    return;
                }

                try {
                    Float canNang = snapshot.child("canNang").getValue(Float.class);
                    Float chieuCao = snapshot.child("chieuCao").getValue(Float.class);
                    String mucTieu = snapshot.child("mucTieu").getValue(String.class);

                    if (canNang == null || chieuCao == null || mucTieu == null) {
                        throw new NullPointerException("Thiếu dữ liệu hồ sơ");
                    }

                    float bmi = calculateBMI(canNang, chieuCao);
                    caloriesTarget = calculateCalories(canNang, chieuCao, mucTieu);

                    txtBMI.setText(String.format("BMI: %.1f", bmi));
                    txtGoalToday.setText(String.format("Mục tiêu hôm nay: %d kcal", caloriesTarget));
                    cardBMIInfo.setVisibility(View.VISIBLE);
                    calculateTotalCalories(); // Update total calories display with target

                } catch (Exception e) {
                    Log.e(TAG, "Error processing profile data", e);
                    Toast.makeText(HomeActivity.this,
                            "Lỗi xử lý dữ liệu hồ sơ", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HomeActivity.this,
                        "Lỗi tải dữ liệu hồ sơ", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Database error: " + error.getMessage());
            }
        });
    }

    private float calculateBMI(float canNang, float chieuCao) {
        return canNang / ((chieuCao / 100) * (chieuCao / 100));
    }

    private int calculateCalories(float canNang, float chieuCao, String mucTieu) {
        int bmr = (int) (10 * canNang + 6.25 * chieuCao - 5 * 25 + 5);
        int maintenanceCalories = (int) (bmr * 1.55);

        switch (mucTieu) {
            case "Tăng cân": return maintenanceCalories + 500;
            case "Giảm cân": return maintenanceCalories - 500;
            default: return maintenanceCalories;
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Already in home
        } else if (id == R.id.nav_profile) {
            startActivity(new Intent(this, ProfileDetailActivity.class));
        } else if (id == R.id.nav_food) {
            startActivity(new Intent(this, FoodActivity.class));
        } else if (id == R.id.nav_logout) {
            firebaseAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}