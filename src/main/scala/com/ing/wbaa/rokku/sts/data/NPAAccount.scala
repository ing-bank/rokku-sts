package com.ing.wbaa.rokku.sts.data

case class NPAAccount(accountName: String, enabled: Boolean)
case class NPAAccountList(data: List[NPAAccount])
