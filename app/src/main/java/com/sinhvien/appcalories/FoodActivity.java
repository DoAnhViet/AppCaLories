package com.sinhvien.appcalories;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import com.sinhvien.appcalories.models.Food;
import java.util.ArrayList;
import java.util.List;

public class FoodActivity extends AppCompatActivity {
    // UI Components
    private EditText edtFoodName, edtFoodCalories;
    private Spinner spinnerCategory;
    private Button btnAdd, btnUpdate, btnDelete;
    private ListView listViewFood;
    private SearchView searchView;

    // Adapter and Data
    private ArrayAdapter<String> listAdapter;
    private List<Food> foodList;
    private String selectedFoodId;

    // Firebase
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.food_activity);

        initViews();
        setupFirebase();
        loadFoodList();
        setupListeners();
    }

    private void initViews() {
        edtFoodName = findViewById(R.id.edtFoodName);
        edtFoodCalories = findViewById(R.id.edtFoodCalories);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        btnAdd = findViewById(R.id.btnAdd);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnDelete = findViewById(R.id.btnDelete);
        listViewFood = findViewById(R.id.listViewFood);
        searchView = findViewById(R.id.searchView);

        // Setup spinner with standardized categories
        String[] categories = {"Bữa sáng", "Bữa trưa", "Bữa chiều", "Bữa tối"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                categories
        );
        spinnerCategory.setAdapter(spinnerAdapter);

        // Setup list view
        foodList = new ArrayList<>();
        listAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        );
        listViewFood.setAdapter(listAdapter);
    }

    private void setupFirebase() {
        databaseReference = FirebaseDatabase.getInstance().getReference("Foods");
    }

    private void loadFoodList() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                foodList.clear();
                List<String> displayList = new ArrayList<>();

                for (DataSnapshot foodSnapshot : snapshot.getChildren()) {
                    try {
                        Food food = foodSnapshot.getValue(Food.class);
                        if (food != null) {
                            // Ensure ID is set (for manual entries)
                            if (food.getId() == null) {
                                food.setId(foodSnapshot.getKey());
                            }
                            foodList.add(food);
                            displayList.add(String.format("%s (%s - %d cal)",
                                    food.getTenMon(),
                                    food.getCategory(),
                                    food.getCalories()));
                        }
                    } catch (Exception e) {
                        Log.e("FoodActivity", "Error parsing food data", e);
                    }
                }

                listAdapter.clear();
                listAdapter.addAll(displayList);
                listAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(FoodActivity.this,
                        "Lỗi tải dữ liệu: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupListeners() {
        btnAdd.setOnClickListener(v -> addFood());
        btnUpdate.setOnClickListener(v -> updateFood());
        btnDelete.setOnClickListener(v -> deleteFood());

        listViewFood.setOnItemClickListener((parent, view, position, id) -> {
            if (position < foodList.size()) {
                Food selectedFood = foodList.get(position);
                selectedFoodId = selectedFood.getId();
                edtFoodName.setText(selectedFood.getTenMon());
                edtFoodCalories.setText(String.valueOf(selectedFood.getCalories()));
                setSpinnerSelection(selectedFood.getCategory());
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterFoodList(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterFoodList(newText);
                return false;
            }
        });
    }

    private void addFood() {
        String tenMon = edtFoodName.getText().toString().trim();
        String caloriesText = edtFoodCalories.getText().toString().trim();

        if (tenMon.isEmpty() || caloriesText.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int calories = Integer.parseInt(caloriesText);
            String category = spinnerCategory.getSelectedItem().toString();
            String foodId = databaseReference.push().getKey();

            Food newFood = new Food(foodId, tenMon, calories, category, "");
            databaseReference.child(foodId).setValue(newFood)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Đã thêm món ăn", Toast.LENGTH_SHORT).show();
                        clearInputs();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Lỗi khi thêm: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Calories phải là số", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFood() {
        if (selectedFoodId == null) {
            Toast.makeText(this, "Vui lòng chọn món ăn cần sửa", Toast.LENGTH_SHORT).show();
            return;
        }

        String tenMon = edtFoodName.getText().toString().trim();
        String caloriesText = edtFoodCalories.getText().toString().trim();

        if (tenMon.isEmpty() || caloriesText.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int calories = Integer.parseInt(caloriesText);
            String category = spinnerCategory.getSelectedItem().toString();

            Food updatedFood = new Food(selectedFoodId, tenMon, calories, category, "");
            databaseReference.child(selectedFoodId).setValue(updatedFood)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Đã cập nhật món ăn", Toast.LENGTH_SHORT).show();
                        clearInputs();
                        selectedFoodId = null;
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Lỗi khi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Calories phải là số", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFood() {
        if (selectedFoodId == null) {
            Toast.makeText(this, "Vui lòng chọn món ăn cần xóa", Toast.LENGTH_SHORT).show();
            return;
        }

        databaseReference.child(selectedFoodId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã xóa món ăn", Toast.LENGTH_SHORT).show();
                    clearInputs();
                    selectedFoodId = null;
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Lỗi khi xóa: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void setSpinnerSelection(String category) {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerCategory.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).equals(category)) {
                spinnerCategory.setSelection(i);
                break;
            }
        }
    }

    private void clearInputs() {
        edtFoodName.setText("");
        edtFoodCalories.setText("");
        spinnerCategory.setSelection(0);
    }

    private void filterFoodList(String query) {
        // Lọc danh sách món ăn theo tên món ăn
        List<String> filteredList = new ArrayList<>();
        for (Food food : foodList) {
            if (food.getTenMon().toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(String.format("%s (%s - %d cal)",
                        food.getTenMon(),
                        food.getCategory(),
                        food.getCalories()));
            }
        }
        listAdapter.clear();
        listAdapter.addAll(filteredList);
        listAdapter.notifyDataSetChanged();
    }
}
