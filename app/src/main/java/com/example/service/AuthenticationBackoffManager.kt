package com.example.service
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock

/** Persistent local backoff for vault password/key-file and recovery attempts; never treats TOTP as a vault factor. */
class AuthenticationBackoffManager internal constructor(
 private val prefs: SharedPreferences, private val wallClock: () -> Long, private val elapsedClock: () -> Long
) {
 constructor(context: Context): this(context.applicationContext.getSharedPreferences(PREFERENCES_NAME,Context.MODE_PRIVATE),{System.currentTimeMillis()},{SystemClock.elapsedRealtime()})
 private var blockedUntilElapsed=0L
 @Synchronized fun isAllowed():Boolean {
  val elapsedNow=elapsedClock()
  if(blockedUntilElapsed>elapsedNow)return false
  val until=prefs.getLong(KEY_UNTIL,0L)
  if(until<=0L){blockedUntilElapsed=0L;return true}
  val wallNow=wallClock()
  val start=prefs.getLong(KEY_START,wallNow)
  val duration=prefs.getLong(KEY_DURATION,0L)
  val wallRemaining=if(wallNow<start)duration else until-wallNow
  if(blockedUntilElapsed==0L)blockedUntilElapsed=elapsedNow+wallRemaining.coerceAtLeast(0L)
  if(blockedUntilElapsed-elapsedClock()>0L||wallRemaining>0L)return false
  blockedUntilElapsed=0L
  prefs.edit().putLong(KEY_UNTIL,0L).putLong(KEY_START,0L).putLong(KEY_DURATION,0L).commit()
  return true
 }
 @Synchronized fun recordFailure(){
  val failures=(prefs.getInt(KEY_FAILURES,0)+1).coerceAtMost(1000)
  val duration=when(failures){5->30_000L;6->60_000L;7->300_000L;in 8..Int.MAX_VALUE->900_000L;else->0L}
  val wall=wallClock();val elapsed=elapsedClock()
  val edit=prefs.edit().putInt(KEY_FAILURES,failures)
  if(duration>0L)edit.putLong(KEY_START,wall).putLong(KEY_DURATION,duration).putLong(KEY_UNTIL,wall+duration)
  val saved=edit.commit()
  if(duration>0L)blockedUntilElapsed=elapsed+if(saved)duration else 900_000L
 }
 @Synchronized fun recordResult(success:Boolean){if(success)recordSuccess() else recordFailure()}
 @Synchronized fun recordSuccess(){blockedUntilElapsed=0L;prefs.edit().putInt(KEY_FAILURES,0).putLong(KEY_UNTIL,0L).putLong(KEY_START,0L).putLong(KEY_DURATION,0L).commit()}
 @Synchronized fun remainingMillis():Long {
  if(isAllowed())return 0L
  return maxOf((prefs.getLong(KEY_UNTIL,0L)-wallClock()).coerceAtLeast(0L),(blockedUntilElapsed-elapsedClock()).coerceAtLeast(0L))
 }
 internal fun failureCountForTests()=prefs.getInt(KEY_FAILURES,0)
 companion object {
  const val PREFERENCES_NAME="mydocanizer_auth_backoff"
  private const val KEY_FAILURES="failure_count";private const val KEY_UNTIL="blocked_until_wall";private const val KEY_START="blocked_started_wall";private const val KEY_DURATION="blocked_duration_ms"
 }
}
