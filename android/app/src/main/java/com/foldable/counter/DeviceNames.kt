package com.foldable.counter

import android.content.Context
import android.os.Build
import android.provider.Settings

/** 모델 코드 → 자막에 쓸 제품명. 표에 없으면 기기 설정의 이름, 그래도 없으면 제조사 + 모델 코드. */
object DeviceNames {

    // 앞부분(prefix)이 일치하면 적용되므로 지역별 접미사(N, B, U, W…)는 신경 쓰지 않아도 됩니다.
    private val PREFIX: List<Pair<String, String>> = listOf(
        // Samsung Galaxy Z Fold
        "SM-F900" to "Galaxy Fold", "SM-F907" to "Galaxy Fold 5G",
        "SM-F916" to "Galaxy Z Fold2", "SM-F926" to "Galaxy Z Fold3", "SM-F936" to "Galaxy Z Fold4",
        "SM-F946" to "Galaxy Z Fold5", "SM-F956" to "Galaxy Z Fold6", "SM-F958" to "Galaxy Z Fold Special Edition",
        "SM-F966" to "Galaxy Z Fold7",
        // Samsung Galaxy Z Flip
        "SM-F700" to "Galaxy Z Flip", "SM-F707" to "Galaxy Z Flip 5G",
        "SM-F711" to "Galaxy Z Flip3", "SM-F721" to "Galaxy Z Flip4", "SM-F731" to "Galaxy Z Flip5",
        "SM-F741" to "Galaxy Z Flip6", "SM-F761" to "Galaxy Z Flip7 FE", "SM-F766" to "Galaxy Z Flip7",
        // Samsung W (China)
        "SM-W2021" to "Galaxy W21 5G", "SM-W2022" to "Galaxy W22 5G",
        "SM-W9023" to "Galaxy W23", "SM-W7023" to "Galaxy W23 Flip",
        "SM-W9024" to "Galaxy W24", "SM-W7024" to "Galaxy W24 Flip",
        "SM-W9025" to "Galaxy W25", "SM-W7025" to "Galaxy W25 Flip",
        // Motorola razr
        "XT2000" to "motorola razr (2019)", "XT2071" to "motorola razr 5G", "XT2251" to "motorola razr 2022",
        "XT2321" to "motorola razr 40 ultra", "XT2323" to "motorola razr 40",
        "XT2453" to "motorola razr 50 ultra", "XT2451" to "motorola razr 50",
        "XT2551" to "motorola razr 60", "XT2553" to "motorola razr 60 ultra",
        // OnePlus / OPPO
        "CPH2551" to "OnePlus Open",
        "PEUM00" to "OPPO Find N", "PGU110" to "OPPO Find N2",
        "PGT110" to "OPPO Find N2 Flip", "CPH2437" to "OPPO Find N2 Flip",
        "PHN110" to "OPPO Find N3", "CPH2499" to "OPPO Find N3",
        "PHT110" to "OPPO Find N3 Flip", "CPH2519" to "OPPO Find N3 Flip",
        "PKH110" to "OPPO Find N5", "CPH2671" to "OPPO Find N5",
        // vivo
        "V2178A" to "vivo X Fold", "V2229A" to "vivo X Fold+", "V2266A" to "vivo X Fold2",
        "V2303A" to "vivo X Fold3", "V2337" to "vivo X Fold3 Pro",
        // Xiaomi
        "M2011J18C" to "Xiaomi MIX FOLD", "22061218C" to "Xiaomi MIX Fold 2",
        "2308CPXD0C" to "Xiaomi MIX Fold 3", "24072PX77C" to "Xiaomi MIX Fold 4",
        "2405CPX3D" to "Xiaomi MIX Flip",
        // HONOR
        "MGI-AN00" to "HONOR Magic V", "FRI-AN00" to "HONOR Magic Vs", "FRI-NX9" to "HONOR Magic Vs",
        "VER-AN10" to "HONOR Magic V2", "VER-N49" to "HONOR Magic V2",
        "FCP-AN10" to "HONOR Magic V3", "FCP-N49" to "HONOR Magic V3",
        // HUAWEI (EMUI, 안드로이드 기반 기기만)
        "TAH-AN00" to "HUAWEI Mate X", "TAH-N29" to "HUAWEI Mate Xs",
        "TET-AN" to "HUAWEI Mate X2", "PAL-AL00" to "HUAWEI Mate Xs 2", "PAL-LX9" to "HUAWEI Mate Xs 2",
        "ALT-AL00" to "HUAWEI Mate X3", "ALT-L29" to "HUAWEI Mate X3", "ALT-AL10" to "HUAWEI Mate X5",
        "BAL-AL00" to "HUAWEI P50 Pocket", "BAL-L49" to "HUAWEI P50 Pocket",
        // TECNO / Royole
        "TECNO AD10" to "TECNO PHANTOM V Fold", "TECNO AD11" to "TECNO PHANTOM V Flip",
        "FlexPai" to "Royole FlexPai",
    )

    fun mapped(model: String): String? {
        val m = model.trim()
        return PREFIX.firstOrNull { m.startsWith(it.first, ignoreCase = true) }?.second
    }

    /** 표에 없을 때: "Pixel Fold", "Surface Duo 2" 처럼 모델명이 이미 제품명이면 그대로, 아니면 제조사 + 모델. */
    fun fallbackName(model: String, manufacturer: String): String {
        val m = model.trim()
        val mf = manufacturer.trim()
        val looksLikeProductName = m.contains(' ') || listOf("fold", "flip", "razr", "duo", "pixel").any { m.contains(it, true) }
        if (looksLikeProductName || mf.isEmpty() || m.startsWith(mf, ignoreCase = true)) return m
        return mf.replaceFirstChar { it.uppercase() } + " " + m
    }

    fun detect(context: Context): String {
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        mapped(model)?.let { return it }
        val settingsName = try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } catch (e: Exception) { null }
        if (!settingsName.isNullOrBlank()) return settingsName.trim()
        return fallbackName(model, manufacturer)
    }
}
