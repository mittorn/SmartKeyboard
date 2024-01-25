/*
 * Copyright (C) 2010-2017 Cyril Deguet
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

package com.dexilog.smartkeyboard;

import android.app.ProgressDialog;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;

public class Settings extends PreferenceActivity {

	private static final String DISABLE_LAUNCHER = "disable_launcher";
	private static final String BACKUP = "backup";
	private static final String RESTORE = "restore";
	private static final String CLEAN_USERDIC = "clean_userdic";
	private static final String EXPORT_USERDIC = "export_user_dic";
	private static final String EXPORT_AUTOTEXT = "export_autotext";
	private static final String IMPORT_USERDIC = "import_user_dic";
	private static final String IMPORT_AUTOTEXT = "import_autotext";
	
	private static final int USERDIC = 0;
	private static final int AUTOTEXT = 1;
	public static final String AUTOTEXT_CSV = "autotext.csv";
	public static final String USERDIC_CSV = "userdic.csv";


	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		FakeInputMethodService.mSettingsChanged = true;
	}

	@Override public void onStop()
	{
		super.onStop();
		FakeInputMethodService.mSettingsChanged = true;
	}
	@Override public void onDestroy()
	{
		super.onDestroy();
		FakeInputMethodService.mSettingsChanged = true;
		FakeInputMethodService.mSingleton.loadSettings(getPreferenceManager().getSharedPreferences());
	}

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		if (preference instanceof CheckBoxPreference) {
			if (preference.getKey().equals(DISABLE_LAUNCHER)) {
				disableMainActivity((CheckBoxPreference)preference);
			}
		} else if (preference != null) {
			final String key = preference.getKey();
			if (key != null) {
				if (key.equals(BACKUP)) {
					backup();
				} else if (key.equals(RESTORE)) {
					restore();
				} else if (key.equals(CLEAN_USERDIC)) {
					cleanUserDic();
				} else if (key.equals(EXPORT_USERDIC)) {
					csvExport(USERDIC);
				} else if (key.equals(EXPORT_AUTOTEXT)) {
					csvExport(AUTOTEXT);
				} else if (key.equals(IMPORT_USERDIC)) {
					csvImport(USERDIC);
				} else if (key.equals(IMPORT_AUTOTEXT)) {
					csvImport(AUTOTEXT);
				}			
			}
		} 
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

	private void disableMainActivity(CheckBoxPreference preference) {
		final boolean disableLauncher = preference.isChecked();
		if (disableLauncher) {
			AlertDialog d = alertBuilder(R.string.disable_launcher_warning)
			.setPositiveButton(android.R.string.ok, new NullClickListener())
			.create();
			d.show();
		}
	}

	private void backup() {
		if (checkSDCard()) {
			AlertDialog d = alertBuilder(R.string.backup_warning)
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {	
					Backup backup = new Backup(Settings.this);
					backup.backup(new Backup.BackupCallback() {
						@Override
						public void onSuccess() {
							Toast.makeText(Settings.this, R.string.backup_complete, Toast.LENGTH_LONG).show();
						}

						@Override
						public void onFailed() {
							Toast.makeText(Settings.this, R.string.backup_failed, Toast.LENGTH_LONG).show();
						}
					});
				}})
				.setNegativeButton(android.R.string.cancel, new NullClickListener())
				.create();
			d.show();
		}
	}

	private void restore() {
		if (checkSDCard()) {
			AlertDialog d = alertBuilder(R.string.restore_warning)
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {	
					Backup backup = new Backup(Settings.this);
					backup.restore(new Backup.BackupCallback() {
						@Override
						public void onSuccess() {
							android.os.Process.killProcess(android.os.Process.myPid());
						}

						@Override
						public void onFailed() {
							Toast.makeText(Settings.this, R.string.restore_failed, Toast.LENGTH_LONG).show();
						}
					});
				}})
				.setNegativeButton(android.R.string.cancel, new NullClickListener())
				.create();
			d.show();
		}
	}
	
	private void cleanUserDic() {
		AlertDialog d = alertBuilder(R.string.warn_clean_userdic)
		.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {	
				getContentResolver().delete(UserDictionaryProvider.CONTENT_URI_WORDS, null, null);
			}})
		.setNegativeButton(android.R.string.cancel, new NullClickListener())
		.create();
		d.show();
	}
	
	private void csvExport(final int what) {
		if (checkSDCard()) {
			int msg = (what == USERDIC) ? R.string.export_user_dic_warning : R.string.export_autotext_warning;
			AlertDialog d = alertBuilder(msg)
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {	
					CSVExporter exporter = new CSVExporter(Settings.this);
					boolean result = false;
					switch (what) {
					case USERDIC:
						exporter.exportUserDic("userdic.csv", new CSVExporter.ExporterCallback() {
							@Override
							public void onSuccess() {
								displayExportComplete();
							}

							@Override
							public void onFailed() {
							}
						});
						break;
					case AUTOTEXT:
						 exporter.exportAutotext("autotext.csv", new CSVExporter.ExporterCallback() {
							 @Override
							 public void onSuccess() {
								 displayExportComplete();
							 }

							 @Override
							 public void onFailed() {
							 }
						 });
						break;
					}
				}})
				.setNegativeButton(android.R.string.cancel, new NullClickListener())
				.create();
			d.show();
		}
	}

	private void displayExportComplete() {
		Toast.makeText(Settings.this, R.string.export_complete, Toast.LENGTH_LONG).show();
	}

	private void csvImport(final int what) {
		if (checkSDCard()) {
			int msg = (what == USERDIC) ? R.string.import_user_dic_warning : R.string.import_autotext_warning;
			AlertDialog d = alertBuilder(msg)
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					CSVExporter exporter = new CSVExporter(Settings.this);
					boolean result = false;
					switch (what) {
					case USERDIC:
						startCvsImport();
						break;
					case AUTOTEXT:
						exporter.importAutotext(AUTOTEXT_CSV, new CSVExporter.ExporterCallback() {
							@Override
							public void onSuccess() {
								displayImportComplete();
							}

							@Override
							public void onFailed() {
							}
						});
					}
				}})
				.setNegativeButton(android.R.string.cancel, new NullClickListener())
				.create();
			d.show();
		}
	}

	private void displayImportComplete() {
		Toast.makeText(Settings.this, R.string.import_complete, Toast.LENGTH_LONG).show();
	}

	private void startCvsImport() {

		final ProgressDialog progressDialog =	new ProgressDialog(this);
		progressDialog.setTitle("CSV Import");
		progressDialog.setMessage("Import in progress...");
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(false);
		progressDialog.show();

		// Start lengthy operation in a background thread
		new Thread(new Runnable() {
			public void run() {
				CSVExporter exporter = new CSVExporter(Settings.this);
				exporter.importUserDic(USERDIC_CSV, new CSVExporter.ExporterCallback() {
					@Override
					public void onSuccess() {
						runOnUiThread(new Runnable() {
							public void run() {
								displayImportComplete();
							}
						});
					}

					@Override
					public void onFailed() {
					}
				});
				progressDialog.dismiss();
			}
		}).start();
	}


	private boolean checkSDCard() {
		final boolean state = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
		if (!state) {
			AlertDialog d = alertBuilder(R.string.no_sd_card)
			.setPositiveButton(android.R.string.cancel, new NullClickListener())
			.create();
			d.show();
		}
		return state;
	}
	
	private AlertDialog.Builder alertBuilder(int msgId) {
		return (new AlertDialog.Builder(this))
		.setTitle(android.R.string.dialog_alert_title)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setMessage(getString(msgId))
		.setCancelable(true);
	}


	static private class NullClickListener implements DialogInterface.OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {	
		}
	}
}
