package com.example
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.AuthenticationBackoffManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE)
class AuthenticationBackoffManagerTest{
 private lateinit var prefs:android.content.SharedPreferences;private var wall=1_800_000_000_000L;private var elapsed=10_000L;private lateinit var manager:AuthenticationBackoffManager
 @Before fun setup(){val c=ApplicationProvider.getApplicationContext<Context>();prefs=c.getSharedPreferences("auth_backoff_test",Context.MODE_PRIVATE);prefs.edit().clear().commit();manager=AuthenticationBackoffManager(prefs,{wall},{elapsed})}
 @Test fun fifthFailureBlocksThirtySecondsThenOneMinute(){repeat(4){assertTrue(manager.isAllowed());manager.recordFailure()};manager.recordFailure();assertFalse(manager.isAllowed());assertEquals(30_000L,manager.remainingMillis());wall+=30_000L;elapsed+=30_000L;assertTrue(manager.isAllowed());manager.recordFailure();assertFalse(manager.isAllowed());assertEquals(60_000L,manager.remainingMillis())}
 @Test fun escalationReachesFiveAndFifteenMinutes(){repeat(5){manager.recordFailure()};wall+=30_000L;elapsed+=30_000L;assertTrue(manager.isAllowed());manager.recordFailure();wall+=60_000L;elapsed+=60_000L;assertTrue(manager.isAllowed());manager.recordFailure();assertEquals(300_000L,manager.remainingMillis());wall+=300_000L;elapsed+=300_000L;assertTrue(manager.isAllowed());manager.recordFailure();assertEquals(900_000L,manager.remainingMillis())}
 @Test fun successClearsFailures(){repeat(5){manager.recordFailure()};assertFalse(manager.isAllowed());manager.recordSuccess();assertTrue(manager.isAllowed());assertEquals(0,manager.failureCountForTests())}

 @Test fun blockSurvivesManagerRecreationAndWallClockRollback(){
  repeat(5){manager.recordFailure()}
  assertFalse(manager.isAllowed())
  wall-=120_000L
  val restarted=AuthenticationBackoffManager(prefs,{wall},{100L})
  assertFalse(restarted.isAllowed())
  assertEquals(30_000L,restarted.remainingMillis())
  wall+=30_000L
  assertFalse(restarted.isAllowed()) // monotonic session deadline remains conservative
 }
 @Test fun failureCounterAndDeadlinePersist(){
  repeat(4){manager.recordFailure()}
  manager.recordFailure()
  val restarted=AuthenticationBackoffManager(prefs,{wall},{100L})
  assertFalse(restarted.isAllowed())
  assertEquals(30_000L,restarted.remainingMillis())
 }
}
