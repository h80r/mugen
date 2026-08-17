package eu.kanade.presentation.more.settings.screen.debug

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.profileinstaller.ProfileVerifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.guava.await
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class DebugInfoScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uiStyle = rememberResolvedSettingsUiStyle()
        PreferenceScaffold(
            titleRes = MR.strings.pref_debug_info,
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
            itemsProvider = {
                listOf(
                    Preference.PreferenceItem.TextPreference(
                        title = WorkerInfoScreen.TITLE,
                        onClick = { navigator.push(WorkerInfoScreen()) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = BackupSchemaScreen.TITLE,
                        onClick = { navigator.push(BackupSchemaScreen()) },
                    ),
                    getAppInfoGroup(),
                    getDeviceInfoGroup(),
                )
            },
        )
    }

    @Composable
    private fun getAppInfoGroup(): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.debug_info_app_info_title),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.version),
                    subtitle = AboutScreen.getVersionName(false),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.debug_info_build_time),
                    subtitle = AboutScreen.getFormattedBuildTime(),
                ),
                getProfileVerifierPreference(),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.debug_info_webview_version),
                    subtitle = getWebViewVersion(),
                ),
            ),
        )
    }

    @Composable
    @ReadOnlyComposable
    private fun getWebViewVersion(): String {
        return WebViewUtil.getVersion(LocalContext.current)
    }

    @Composable
    private fun getProfileVerifierPreference(): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        val noProfileInstalled = stringResource(MR.strings.debug_info_profile_status_none)
        val compiled = stringResource(MR.strings.debug_info_profile_status_compiled)
        val compiledNonMatching = stringResource(MR.strings.debug_info_profile_status_compiled_non_matching)
        val unsupported = stringResource(MR.strings.debug_info_profile_status_unsupported)
        val pending = stringResource(MR.strings.debug_info_profile_status_pending)
        val noProfileEmbedded = stringResource(MR.strings.debug_info_profile_status_no_profile_embedded)
        val status by produceState(initialValue = "-") {
            val result = ProfileVerifier.getCompilationStatusAsync().await().profileInstallResultCode
            value = when (result) {
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_NO_PROFILE_INSTALLED,
                -> noProfileInstalled
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_COMPILED_WITH_PROFILE,
                -> compiled
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_COMPILED_WITH_PROFILE_NON_MATCHING,
                -> compiledNonMatching
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_ERROR_CACHE_FILE_EXISTS_BUT_CANNOT_BE_READ,
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_ERROR_CANT_WRITE_PROFILE_VERIFICATION_RESULT_CACHE_FILE,
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_ERROR_PACKAGE_NAME_DOES_NOT_EXIST,
                -> context.stringResource(MR.strings.debug_info_profile_status_error, result)
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_ERROR_UNSUPPORTED_API_VERSION,
                -> unsupported
                ProfileVerifier.CompilationStatus
                    .RESULT_CODE_PROFILE_ENQUEUED_FOR_COMPILATION,
                -> pending
                ProfileVerifier.CompilationStatus.RESULT_CODE_ERROR_NO_PROFILE_EMBEDDED -> noProfileEmbedded
                else -> context.stringResource(MR.strings.debug_info_profile_status_unknown, result)
            }
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.debug_info_profile_status_title),
            subtitle = status,
        )
    }

    @Composable
    private fun getDeviceInfoGroup(): Preference.PreferenceGroup {
        val modelTitle = stringResource(MR.strings.debug_info_model)
        val oneUiTitle = stringResource(MR.strings.debug_info_oneui_version)
        val miuiTitle = stringResource(MR.strings.debug_info_miui_version)
        val androidVersionTitle = stringResource(MR.strings.debug_info_android_version)
        val items = persistentListOf<Preference.PreferenceItem<out Any>>().mutate {
            it.add(
                Preference.PreferenceItem.TextPreference(
                    title = modelTitle,
                    subtitle = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                ),
            )

            if (DeviceUtil.oneUiVersion != null) {
                it.add(
                    Preference.PreferenceItem.TextPreference(
                        title = oneUiTitle,
                        subtitle = "${DeviceUtil.oneUiVersion}",
                    ),
                )
            } else if (DeviceUtil.miuiMajorVersion != null) {
                it.add(
                    Preference.PreferenceItem.TextPreference(
                        title = miuiTitle,
                        subtitle = "${DeviceUtil.miuiMajorVersion}",
                    ),
                )
            }

            val androidVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Build.VERSION.RELEASE_OR_CODENAME
            } else {
                Build.VERSION.RELEASE
            }
            it.add(
                Preference.PreferenceItem.TextPreference(
                    title = androidVersionTitle,
                    subtitle = "$androidVersion (${Build.DISPLAY})",
                ),
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.debug_info_device_info_title),
            preferenceItems = items,
        )
    }
}
