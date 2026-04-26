package com.grokchat.pro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.grokchat.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()

        b.etApiKey.setText(Prefs.getApiKey(ctx))

        val endpointLabels = Prefs.ENDPOINTS.map { it.second }
        b.spinnerEndpoint.adapter = ArrayAdapter(ctx,
            android.R.layout.simple_spinner_item, endpointLabels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val epIdx = Prefs.ENDPOINTS.indexOfFirst { it.first == Prefs.getEndpoint(ctx) }.coerceAtLeast(0)
        b.spinnerEndpoint.setSelection(epIdx)

        b.spinnerModel.adapter = ArrayAdapter(ctx,
            android.R.layout.simple_spinner_item, Prefs.MODELS)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val mdIdx = Prefs.MODELS.indexOf(Prefs.getModel(ctx)).coerceAtLeast(0)
        b.spinnerModel.setSelection(mdIdx)

        b.btnSave.setOnClickListener {
            Prefs.setApiKey(ctx, b.etApiKey.text.toString().trim())
            Prefs.setEndpoint(ctx, Prefs.ENDPOINTS[b.spinnerEndpoint.selectedItemPosition].first)
            Prefs.setModel(ctx, Prefs.MODELS[b.spinnerModel.selectedItemPosition])
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
