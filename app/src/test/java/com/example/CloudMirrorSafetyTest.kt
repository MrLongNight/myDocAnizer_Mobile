package com.example
import com.example.model.CloudSyncConfig
import org.junit.Assert.assertFalse
import org.junit.Test
class CloudMirrorSafetyTest{@Test fun autoMirrorIsDisabledByDefault(){assertFalse(CloudSyncConfig().autoMirrorToBackupDir)}}
