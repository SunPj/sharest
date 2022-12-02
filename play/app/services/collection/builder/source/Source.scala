package com.github.sunpj.shaytan.services.collection.builder.source

import com.github.sunpj.shaytan.services.collection.Collection
import com.github.sunpj.shaytan.services.collection.builder.CollectionRules


trait SourceTypes {
  type Fields
  type Filter
}

trait Source[S <: SourceTypes] { self =>
  def toCollection(rules: CollectionRules[S]): Collection
}
