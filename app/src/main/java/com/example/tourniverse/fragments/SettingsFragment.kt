package com.example.tourniverse.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tourniverse.LoginActivity
import com.example.tourniverse.R
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Account Settings Click Listener
        view.findViewById<TextView>(R.id.accountSettings).setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_accountFragment)
        }

        // Notification Settings Click Listener
        view.findViewById<TextView>(R.id.notificationSettings).setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_notificationFragment)
        }

        // Help & Support Click Listener
        view.findViewById<TextView>(R.id.helpSupport).setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_helpSupportFragment)
        }

        // About Click Listener
        view.findViewById<TextView>(R.id.aboutSettings).setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_aboutFragment)
        }

        // Sign Out Click Listener
        view.findViewById<TextView>(R.id.signOut).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(context, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Toast.makeText(context, "Signed Out Successfully", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
