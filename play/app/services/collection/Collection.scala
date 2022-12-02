package com.github.sunpj.sharest.services.collection

import com.github.sunpj.sharest.services.collection.CollectionService.{Res, UnitRes}
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

trait Collection {
    def create(model: JsValue)(implicit r: RequestHeader): UnitRes
    def get(id: String)(implicit r: RequestHeader): Res[Option[JsValue]]
    def update(id: String, model: JsValue)(implicit r: RequestHeader): UnitRes
    def delete(id: String)(implicit r: RequestHeader): UnitRes
    def fetch(filter: Option[Filter], sort: Option[Sort], paging: Option[Paging])(implicit r: RequestHeader): Res[(Seq[JsValue], Long)]
}
