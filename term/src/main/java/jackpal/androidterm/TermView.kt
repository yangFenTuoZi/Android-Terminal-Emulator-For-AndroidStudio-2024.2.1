/*
 * Copyright (C) 2012 Steven Luo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm

import android.annotation.SuppressLint
import android.content.Context
import android.util.DisplayMetrics
import jackpal.androidterm.emulatorview.ColorScheme
import jackpal.androidterm.emulatorview.EmulatorView
import jackpal.androidterm.emulatorview.TermSession
import jackpal.androidterm.util.TermSettings

@SuppressLint("ViewConstructor")
class TermView(context: Context, session: TermSession, metrics: DisplayMetrics) : EmulatorView(context, session, metrics) {
    fun updatePrefs(settings: TermSettings, scheme: ColorScheme? = null) {
        val colorScheme = scheme ?: ColorScheme(settings.colorScheme)
        setTextSize(settings.fontSize)
        setUseCookedIME(settings.useCookedIME())
        setColorScheme(colorScheme)
        setBackKeyCharacter(settings.backKeyCharacter)
        setAltSendsEsc(settings.altSendsEscFlag)
        setControlKeyCode(settings.controlKeyCode)
        setFnKeyCode(settings.fnKeyCode)
        setTermType(settings.termType)
        setMouseTracking(settings.mouseTrackingFlag)
    }

    override fun toString(): String {
        return this::class.toString() + "(" + termSession + ")"
    }
}

