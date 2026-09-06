package com.example.vibrationtester

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minden felfedezett, futásidejűen ellenőrzött rezgés-paramétert felkínál
 * beállításra: erősség, hossz, szünet, kiszámíthatatlanság mértéke, előre
 * definiált effektusok és összetett primitívek (ahol az API-szint és a
 * hardver engedi), valamint a gomb-ismétlési viselkedést. Amit a készülék
 * nem támogat, azt nem elrejti, hanem láthatóan jelzi, hogy miért nem
 * elérhető - lásd VibrationCapabilities.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: VibrationSettings
    private lateinit var vibrator: Vibrator
    private lateinit var report: VibrationCapabilities.Report

    private lateinit var chkUseCustomAmplitude: CheckBox
    private lateinit var seekAmplitude: SeekBar
    private lateinit var lblAmplitudeValue: TextView
    private lateinit var editDuration: android.widget.EditText
    private lateinit var editPause: android.widget.EditText
    private lateinit var seekUnpredictability: SeekBar
    private lateinit var lblUnpredictabilityValue: TextView
    private lateinit var radioVibrationType: RadioGroup
    private lateinit var panelPredefinedEffect: LinearLayout
    private lateinit var panelComposition: LinearLayout
    private lateinit var radioPredefinedEffects: RadioGroup
    private lateinit var containerPrimitives: LinearLayout
    private lateinit var chkRestartOnRepeatPress: CheckBox

    // Egyszerű flag, ami elnyomja a callback-eket a mezők kezdeti feltöltése
    // közben, hogy az ne indítson felesleges/hibás mentést.
    private var isPopulating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = VibrationSettings(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        report = VibrationCapabilities.buildReport(vibrator)

        bindViews()
        renderCapabilities()
        populateAmplitudeSection()
        populateDurationAndPause()
        populateUnpredictability()
        populateVibrationTypeSection()
        populateRepeatPressSection()
    }

    private fun bindViews() {
        chkUseCustomAmplitude = findViewById(R.id.chkUseCustomAmplitude)
        seekAmplitude = findViewById(R.id.seekAmplitude)
        lblAmplitudeValue = findViewById(R.id.lblAmplitudeValue)
        editDuration = findViewById(R.id.editDuration)
        editPause = findViewById(R.id.editPause)
        seekUnpredictability = findViewById(R.id.seekUnpredictability)
        lblUnpredictabilityValue = findViewById(R.id.lblUnpredictabilityValue)
        radioVibrationType = findViewById(R.id.radioVibrationType)
        panelPredefinedEffect = findViewById(R.id.panelPredefinedEffect)
        panelComposition = findViewById(R.id.panelComposition)
        radioPredefinedEffects = findViewById(R.id.radioPredefinedEffects)
        containerPrimitives = findViewById(R.id.containerPrimitives)
        chkRestartOnRepeatPress = findViewById(R.id.chkRestartOnRepeatPress)
    }

    // --- Device capability report (read-only) -------------------------------

    private fun renderCapabilities() {
        val container = findViewById<LinearLayout>(R.id.containerCapabilities)
        container.removeAllViews()

        addCapabilityLine(container, getString(R.string.capability_sdk_version, report.sdkInt))
        addCapabilityLine(
            container,
            getString(if (report.hasVibrator) R.string.capability_has_vibrator_yes else R.string.capability_has_vibrator_no)
        )

        if (!report.amplitudeControlApiExists) {
            addCapabilityLine(container, getString(R.string.capability_amplitude_os_unavailable))
        } else {
            addCapabilityLine(
                container,
                getString(if (report.hasAmplitudeControl) R.string.capability_amplitude_yes else R.string.capability_amplitude_no)
            )
        }

        if (!report.predefinedEffectsApiExists) {
            addCapabilityLine(container, getString(R.string.capability_predefined_os_unavailable))
        } else {
            for (effect in report.predefinedEffects) {
                val supportLabel = supportLabelFor(effect.support)
                val note = if (!report.effectSupportQueryApiExists) " ${getString(R.string.support_unreliable_note)}" else ""
                addCapabilityLine(container, "${getString(effect.nameRes)}: $supportLabel$note")
            }
        }

        if (!report.compositionApiExists) {
            addCapabilityLine(container, getString(R.string.capability_composition_os_unavailable))
        } else {
            for (primitive in report.primitives) {
                val supportLabel = getString(if (primitive.supported) R.string.support_yes else R.string.support_no)
                val note = if (!primitive.checkReliable) " ${getString(R.string.support_unreliable_note)}" else ""
                addCapabilityLine(container, "${getString(primitive.nameRes)}: $supportLabel$note")
            }
        }

        val freqText = report.resonantFrequencyHz?.let { getString(R.string.capability_resonant_frequency, it) }
            ?: getString(R.string.capability_resonant_frequency_unavailable)
        addCapabilityLine(container, freqText)

        val qText = report.qFactor?.let { getString(R.string.capability_q_factor, it) }
            ?: getString(R.string.capability_q_factor_unavailable)
        addCapabilityLine(container, qText)
    }

    private fun supportLabelFor(support: VibrationCapabilities.Support): String = when (support) {
        VibrationCapabilities.Support.YES -> getString(R.string.support_yes)
        VibrationCapabilities.Support.NO -> getString(R.string.support_no)
        VibrationCapabilities.Support.UNKNOWN -> getString(R.string.support_unknown)
    }

    private fun addCapabilityLine(container: LinearLayout, text: String) {
        val tv = TextView(this)
        tv.text = text
        TextViewCompatStyle.apply(tv)
        container.addView(tv)
    }

    // --- Amplitude ------------------------------------------------------------

    private fun populateAmplitudeSection() {
        val amplitudeAvailable = report.hasAmplitudeControl
        chkUseCustomAmplitude.isEnabled = amplitudeAvailable
        seekAmplitude.isEnabled = amplitudeAvailable

        isPopulating = true
        chkUseCustomAmplitude.isChecked = amplitudeAvailable && settings.useCustomAmplitude
        seekAmplitude.progress = (settings.customAmplitude - 1).coerceIn(0, 254)
        updateAmplitudeLabel()
        isPopulating = false

        chkUseCustomAmplitude.setOnCheckedChangeListener { _, isChecked ->
            if (isPopulating) return@setOnCheckedChangeListener
            settings.useCustomAmplitude = isChecked
            seekAmplitude.isEnabled = isChecked && amplitudeAvailable
        }
        seekAmplitude.isEnabled = chkUseCustomAmplitude.isChecked && amplitudeAvailable

        seekAmplitude.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateAmplitudeLabel()
                if (fromUser) settings.customAmplitude = progress + 1
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateAmplitudeLabel() {
        lblAmplitudeValue.text = getString(R.string.amplitude_value_format, seekAmplitude.progress + 1)
    }

    // --- Duration / pause -------------------------------------------------

    private fun populateDurationAndPause() {
        editDuration.setText(settings.durationMs.toString())
        editPause.setText(settings.pauseMs.toString())

        editDuration.addTextChangedListener(simpleWatcher { text ->
            text.toString().toLongOrNull()?.let { settings.durationMs = it }
        })
        editPause.addTextChangedListener(simpleWatcher { text ->
            text.toString().toLongOrNull()?.let { settings.pauseMs = it }
        })
    }

    // --- Unpredictability ---------------------------------------------------

    private fun populateUnpredictability() {
        seekUnpredictability.progress = settings.unpredictabilityPercent
        updateUnpredictabilityLabel()
        seekUnpredictability.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateUnpredictabilityLabel()
                if (fromUser) settings.unpredictabilityPercent = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateUnpredictabilityLabel() {
        lblUnpredictabilityValue.text = getString(R.string.unpredictability_value_format, seekUnpredictability.progress)
    }

    // --- Vibration type: custom / predefined / composition -----------------

    private fun populateVibrationTypeSection() {
        val radioCustom = findViewById<RadioButton>(R.id.radioTypeCustom)
        val radioPredefined = findViewById<RadioButton>(R.id.radioTypePredefined)
        val radioComposition = findViewById<RadioButton>(R.id.radioTypeComposition)

        radioPredefined.isEnabled = report.predefinedEffectsApiExists
        radioComposition.isEnabled = report.compositionApiExists
        if (!report.predefinedEffectsApiExists) {
            radioPredefined.text = "${radioPredefined.text} - ${getString(R.string.capability_predefined_os_unavailable)}"
        }
        if (!report.compositionApiExists) {
            radioComposition.text = "${radioComposition.text} - ${getString(R.string.capability_composition_os_unavailable)}"
        }

        populatePredefinedEffectChoices()
        populatePrimitiveChoices()

        val currentType = settings.vibrationType
        isPopulating = true
        when (currentType) {
            VibrationSettings.VibrationType.CUSTOM -> radioCustom.isChecked = true
            VibrationSettings.VibrationType.PREDEFINED ->
                if (report.predefinedEffectsApiExists) radioPredefined.isChecked = true else radioCustom.isChecked = true
            VibrationSettings.VibrationType.COMPOSITION ->
                if (report.compositionApiExists) radioComposition.isChecked = true else radioCustom.isChecked = true
        }
        updateTypePanelVisibility()
        isPopulating = false

        radioVibrationType.setOnCheckedChangeListener { _, checkedId ->
            if (isPopulating) return@setOnCheckedChangeListener
            settings.vibrationType = when (checkedId) {
                R.id.radioTypePredefined -> VibrationSettings.VibrationType.PREDEFINED
                R.id.radioTypeComposition -> VibrationSettings.VibrationType.COMPOSITION
                else -> VibrationSettings.VibrationType.CUSTOM
            }
            updateTypePanelVisibility()
        }
    }

    private fun updateTypePanelVisibility() {
        panelPredefinedEffect.visibility =
            if (settings.vibrationType == VibrationSettings.VibrationType.PREDEFINED) android.view.View.VISIBLE else android.view.View.GONE
        panelComposition.visibility =
            if (settings.vibrationType == VibrationSettings.VibrationType.COMPOSITION) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun populatePredefinedEffectChoices() {
        if (!report.predefinedEffectsApiExists) return
        radioPredefinedEffects.removeAllViews()
        val savedId = settings.predefinedEffectId
        report.predefinedEffects.forEach { effect ->
            val rb = RadioButton(this)
            rb.id = android.view.View.generateViewId()
            rb.text = "${getString(effect.nameRes)} (${supportLabelFor(effect.support)})"
            rb.setTextColor(0xFFFFFFFF.toInt())
            rb.tag = effect.id
            radioPredefinedEffects.addView(rb)
            if (effect.id == savedId) rb.isChecked = true
        }
        radioPredefinedEffects.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            val effectId = rb.tag as? Int ?: return@setOnCheckedChangeListener
            settings.predefinedEffectId = effectId
        }
    }

    private fun populatePrimitiveChoices() {
        if (!report.compositionApiExists) return
        containerPrimitives.removeAllViews()
        val saved = settings.selectedPrimitiveIds
        report.primitives.forEach { primitive ->
            val cb = CheckBox(this)
            val supportLabel = getString(if (primitive.supported) R.string.support_yes else R.string.support_no)
            val note = if (!primitive.checkReliable) " ${getString(R.string.support_unreliable_note)}" else ""
            cb.text = "${getString(primitive.nameRes)} ($supportLabel$note)"
            cb.setTextColor(0xFFFFFFFF.toInt())
            cb.isChecked = saved.contains(primitive.id)
            cb.setOnCheckedChangeListener { _, _ -> persistSelectedPrimitives() }
            cb.tag = primitive.id
            containerPrimitives.addView(cb)
        }
    }

    private fun persistSelectedPrimitives() {
        val selected = mutableSetOf<Int>()
        for (i in 0 until containerPrimitives.childCount) {
            val cb = containerPrimitives.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) (cb.tag as? Int)?.let { selected.add(it) }
        }
        settings.selectedPrimitiveIds = selected
    }

    // --- Repeat-press behaviour ----------------------------------------------

    private fun populateRepeatPressSection() {
        chkRestartOnRepeatPress.isChecked = settings.restartOnRepeatPress
        chkRestartOnRepeatPress.setOnCheckedChangeListener { _, isChecked ->
            settings.restartOnRepeatPress = isChecked
        }
    }

    // --- Small helpers -----------------------------------------------------

    private fun simpleWatcher(onChanged: (Editable?) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = onChanged(s)
    }

    /** Egységes stílus a dinamikusan létrehozott képesség-sorokhoz. */
    private object TextViewCompatStyle {
        fun apply(tv: TextView) {
            tv.setTextColor(0xFFDDDDDD.toInt())
            tv.textSize = 14f
            tv.setPadding(0, 0, 0, dpToPx(tv, 4))
            tv.gravity = Gravity.START
        }
        private fun dpToPx(tv: TextView, dp: Int): Int =
            (dp * tv.resources.displayMetrics.density).toInt()
    }
}
