package com.verto.ui.common

import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.StepEnergy

fun EnergyLevel.label(): String = when (this) {
    EnergyLevel.Low -> "baixa"
    EnergyLevel.Medium -> "média"
    EnergyLevel.High -> "alta"
}

fun StepEnergy.label(): String = when (this) {
    StepEnergy.Low -> "leve"
    StepEnergy.Medium -> "média"
    StepEnergy.Hyperfocus -> "hiperfoco"
}
