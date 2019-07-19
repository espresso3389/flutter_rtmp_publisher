package jp.espresso3389.flutter_rtmp_publisher

import android.content.pm.PackageManager
import android.os.Handler
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.PluginRegistry

typealias OnPermissionGranted = (allGranted: Boolean) -> Unit

class PermissionRequester private constructor(private val registrar: PluginRegistry.Registrar) : PluginRegistry.RequestPermissionsResultListener {
  private val handler = Handler()
  private val requestCode = 3424815
  private var permissionsGranted: OnPermissionGranted? = null

  init {
    registrar.addRequestPermissionsResultListener(this)
  }


  private fun requestPermission(permissions: Array<String>, permissionsGranted: OnPermissionGranted) {
    val perms = ArrayList<String>()
    for (i in permissions.indices) {
      val permission = permissions[i]
      val permissionCheck = ContextCompat.checkSelfPermission(registrar.context(), permission)
      if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
        perms.add(permission)
      }
    }
    if (perms.isEmpty()) {
      permissionsGranted(true)
      return
    }

    this.permissionsGranted = permissionsGranted
    ActivityCompat.requestPermissions(registrar.activity(), perms.toTypedArray(), requestCode)
  }

  override fun onRequestPermissionsResult(id: Int, permissions: Array<String>, results: IntArray): Boolean {
    if (permissionsGranted != null && id == requestCode) {
      var count = 0
      for (r in results) {
        if (r == PackageManager.PERMISSION_GRANTED)
          count++
      }
      val tmp = permissionsGranted
      permissionsGranted = null
      handler.post { tmp!!(results.size == count) }
      return true
    }
    return false
  }

  companion object {

    private var instance: PermissionRequester? = null

    fun requestPermissions(registrar: PluginRegistry.Registrar, permissions: Array<String>, permissionsGranted: OnPermissionGranted) {
      if (instance == null) {
        instance = PermissionRequester(registrar)
      }
      instance!!.requestPermission(permissions, permissionsGranted)
    }

    fun requestPermission(registrar: PluginRegistry.Registrar, permission: String, permissionsGranted: OnPermissionGranted) {
      requestPermissions(registrar, arrayOf(permission), permissionsGranted)
    }
  }
}
