package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.helpers.FileHelper
import com.ismartcoding.plain.helpers.IntruderCaptureHelper

data class IntruderCaptureModel(
    val id: String,
    val timestamp: Long,
    val trigger: String,
    val triggerDetail: String,
    val hasPhoto: Boolean,
    val fileId: String,
    val lat: Double,
    val lng: Double,
    val hasLocation: Boolean,
)

private fun IntruderCaptureHelper.Capture.toModel(): IntruderCaptureModel {
    val fid = if (absPath.isNotEmpty()) {
        try { FileHelper.getFileId(absPath) } catch (_: Throwable) { "" }
    } else ""
    return IntruderCaptureModel(
        id = id,
        timestamp = timestamp,
        trigger = trigger,
        triggerDetail = triggerDetail,
        hasPhoto = fid.isNotEmpty(),
        fileId = fid,
        lat = lat,
        lng = lng,
        hasLocation = hasLocation,
    )
}

fun SchemaBuilder.addIntruderCapturesSchema() {

    type<IntruderCaptureModel> {}

    query("intruderCaptures") {
        resolver { offset: Int, limit: Int ->
            val all = IntruderCaptureHelper.list()
            if (offset >= all.size) emptyList()
            else {
                val end = (offset + limit).coerceAtMost(all.size)
                all.subList(offset, end).map { it.toModel() }
            }
        }
    }

    query("intruderCapturesCount") {
        resolver { -> IntruderCaptureHelper.count() }
    }

    mutation("deleteIntruderCaptures") {
        resolver { ids: List<String> ->
            IntruderCaptureHelper.deleteByIds(ids)
        }
    }

    mutation("clearIntruderCaptures") {
        resolver { ->
            IntruderCaptureHelper.clearAll()
        }
    }
}
