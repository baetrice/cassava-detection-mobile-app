package com.example.licenta.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bluejamesbond.text.DocumentView;
import com.bluejamesbond.text.style.TextAlignment;
import com.example.licenta.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textHomeTitle = binding.textHomeTitle;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textHomeTitle::setText);

        final DocumentView textAppInfo = binding.textAppInfo;
        homeViewModel.getAppInfo().observe(getViewLifecycleOwner(), text -> {
            textAppInfo.setText(text);
            textAppInfo.getDocumentLayoutParams().setTextAlignment(TextAlignment.JUSTIFIED);
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
