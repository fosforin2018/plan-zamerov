package com.zamerplan.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViewsService

class ZamerWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return ZamerWidgetFactory(this.applicationContext, intent)
    }
}
