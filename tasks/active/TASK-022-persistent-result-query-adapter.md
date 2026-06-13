# TASK-022 Persistent Result Query Adapter 閺堚偓鐏忓繑瀵旀稊鍛閺屻儴顕楅柅鍌炲帳鐏?
状态：已完成并提交
类型：A 类核心链路后端开发
优先级：P0
负责人：Codex

閺夈儲绨敍姝欳URRENT_CONTEXT.md`閵嗕梗tasks/MVP_TASK_MAP.md`閵嗕梗tasks/TEMPLATE_ROUTER.md`

## 閼冲本娅?
`TASK-021` 瀹稿弶褰佹笟?`GET /api/v1/tasks/{taskId}/result` 閺堚偓鐏忓繐褰х拠缁樼叀鐠囥垺甯撮崣锝冣偓?
瑜版挸澧?`TASK-021` 閻ㄥ嫮绮ㄩ弸婊勫閹恒儰绮涢弰?`InMemoryTaskResultStore`閿涘苯褰ч柅鍌氭値 MVP 閸愬懎鐡ㄩ幀渚€妫撮悳顖ょ礉娑撳秵妲搁張鈧紒鍫熷瘮娑斿懎瀵茬紒鎾寸亯閺屻儴顕楃€圭偟骞囬妴?
`TASK-022` 閻ㄥ嫮娲伴弽鍥︾瑝閺勵垱鏌婃晶鐐村复閸欙綇绱濋懓灞炬Ц閹跺﹥鐓＄拠銏″复閸欙絽鎮楅棃銏㈡畱缂佹挻鐏夐弶銉︾爱閿涘奔绮犻崘鍛摠閹焦澹欓幒銉ョ湴閹恒劏绻橀崚鎵埂鐎圭偞鏆熼幑顔肩氨 query adapter閿涘奔绱崗鍫ユ桨閸氭垵鍑￠張?V1 schema 閻?`review_result_snapshot / execution / task` 鐠囶厺绠熼妴?
## 娴犺濮熼惄顔界垼

閺堫剝鐤嗛崣顏勪粵閻栨湹鎹㈤崝鈥崇紦濡楋綇绱濇稉宥呭晸娴狅絿鐖滈妴?
閸氬海鐢荤€圭偟骞囬惄顔界垼閿?
1. 婢х偛濮為張鈧亸蹇斿瘮娑斿懎瀵茬紒鎾寸亯閺屻儴顕楅柅鍌炲帳鐏炲倶鈧?2. 鐠?`TASK-021` 瀹稿弶婀侀弻銉嚄閹恒儱褰涢崣顖欎簰娴犲孩瀵旀稊鍛缂佹挻鐏夐弶銉︾爱鐠囪褰?`ReviewResultSnapshot` 閹存牗娓剁亸蹇撳讲鏉╂柨娲栫紒鎾寸亯閵?3. 娣囨繃瀵?`GET /api/v1/tasks/{taskId}/result` 閹恒儱褰涚捄顖氱窞閸滃苯顦婚柈銊嚔娑斿绗夐崣妯糕偓?4. 娑撳秷袝閸欐垵顓搁弽鎼炩偓?5. 娑撳秹鍣哥捄?`TaskExecutionStateMachine`閵?6. 娑撳秵鏁奸崣?execution 閻樿埖鈧降鈧?7. 娑撳秴鍟?stage log閵?8. 鐏忎粙鍣烘稉宥嗘暭閺佺増宓佹惔?schema閵?
## 娴犺濮熼幀褑宸?
A 缁粯鐗宠箛鍐懠鐠侯垰鎮楃粩顖氱磻閸欐埊绱濋悽?Codex 娑撶粯甯堕妴?
## Task Context

### Required Context

* `AGENTS.md`
* `CURRENT_CONTEXT.md`
* `tasks/MVP_TASK_MAP.md`
* `tasks/TEMPLATE_ROUTER.md`
* 閺堫兛鎹㈤崝鈩冩瀮娴?* `tasks/active/TASK-021-result-url-query-api.md`

### Optional Context

* `docs/backend.md`
* `docs/database.md`
* `docs/context-management.md`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/InMemoryTaskResultStore.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQueryService.java`
* `apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java`

### Out of Scope

* 閸撳秶顏い鐢告桨
* 閺傞鎹㈤崝鈥冲灡瀵ょ儤甯撮崣?* 娴犺濮熼柌宥嗘煀閹笛嗩攽
* 瀵倹顒炵拫鍐ㄥ缁崵绮?* 闁村瓨娼?/ 閺夊啴妾哄Ο鈥崇€?* 缂佹挻鐏夋稉瀣祰
* `PRD.md`
* `docs/ARCHITECTURE.md`
* Docker 闁板秶鐤?
## 閺勫海鈥樻稉宥呬粵

* 娑撳秵鏌婃晶鐐插缁旑垶銆夐棃?* 娑撳秵鏌婃晶鐐版崲閸斺€冲灡瀵ょ儤甯撮崣?* 娑撳秴浠涘鍌涱劄鐠嬪啫瀹崇化鑽ょ埠
* 娑撳秴浠涢柎瀛樻綀 / 閺夊啴妾哄Ο鈥崇€?* 娑撳秴浠涚紒鎾寸亯娑撳娴?* 娑撳秳鎱ㄩ弨?`PRD.md`
* 娑撳秳鎱ㄩ弨?`docs/ARCHITECTURE.md`
* 娑撳秵鏁?Docker 闁板秶鐤?* 娑撳秹鍣搁弸?`TASK-019 ResultComposer`
* 娑撳秹鍣搁弸?`TASK-020 TaskExecutionStateMachine`
* 娑撳秵鏁?`TASK-021` 鐎电懓顦婚幒銉ュ經鐠侯垰绶?* 娑撳秴绱╅崗銉ヮ槻閺?repository 閺嬭埖鐎?* 娑撳秴浠涙径褑瀵栭崶瀛樻殶閹诡喖绨卞Ο鈥崇€烽弨褰掆偓?
婵″倹鐏夌€圭偟骞囬崜宥呭絺閻滄壆骞囬張?schema 閺冪姵纭堕弨顖涘瘮閺堚偓鐏忓繑瀵旀稊鍛閺屻儴顕楅敍灞界安閺嗗倸浠犻幎銉ユ啞閿涘奔绗夊妤冩纯閹恒儰鎱ㄩ弨纭呯讣缁夋眹鈧?
## 鐎圭偟骞囬崜宥呯箑妞ょ粯甯伴弻銉ф畱闂傤噣顣?
1. 瑜版挸澧?V1 schema 娑?`task / execution / review_result_snapshot / task_stage_log` 閻ㄥ嫮婀＄€圭偛鐡у▓鐐光偓?2. 瑜版挸澧犳い鍦窗閺勵垰鎯佸鍙夋箒 JPA / JDBC / Repository 娴ｈ法鏁ら弬鐟扮础閵?3. 瑜版挸澧犲ù瀣槸閺勵垰鎯侀柅鍌氭値娴ｈ法鏁?Testcontainers閵嗕讣pringBootTest閵嗕福dbcTemplate閿涘本鍨ㄩ崣顏勪粵閺囩鏉介惃鍕偓鍌炲帳鐏炲倸宕熷ù瀣ㄢ偓?4. `ReviewResultSnapshot` 瑜版挸澧犻弰顖氭儊閸欘垳娲块幒銉ョ碍閸掓瀵?/ 閸欏秴绨崚妤€瀵查妴?5. `TASK-021 InMemoryTaskResultStore` 婵″倷缍嶉弴鎸庡床閹存牗濞婄挒鈥茶礋閹镐椒绠欓崠鏍ㄧ叀鐠囥垺娼靛┃鎰┾偓?6. 閺勵垰鎯侀棁鈧憰浣风箽閻ｆ瑥鍞寸€涙ɑ鈧椒缍旀稉鐑樼ゴ鐠囨洘娴涢煬顐犫偓?7. 閺勵垰鎯侀棁鈧憰浣规殶閹诡喖绨辨潻浣盒╅敍娑㈢帛鐠併倗娲伴弽鍥ㄦЦ娑撳秹娓剁憰浣碘偓?
## 妫板嫭婀″☉澶婂挤濡€虫健

* `reviewengine`
* `task / execution / review_result_snapshot` 閺屻儴顕楅幍鎸庡复鐏?* 閹镐椒绠欓崠鏍ㄧ叀鐠囥垽鈧倿鍘ょ仦?* 閸氬海顏€规艾鎮滃ù瀣槸

## 妤犲本鏁归弽鍥у櫙

閸氬海鐢荤€圭偟骞囬梼鑸殿唽閼峰啿鐨陇鍐婚敍?
* 瀹稿弶婀佺紒鎾寸亯閸欘垯浜掓禒搴㈠瘮娑斿懎瀵查弶銉︾爱閺屻儴顕楅獮鎯扮箲閸?`200`
* 娑撳秴鐡ㄩ崷?`taskId` 鏉╂柨娲?`404`
* `task` 鐎涙ê婀担鍡樼梾閺?result snapshot 鏉╂柨娲?`409` 閹存牗閮ㄩ悽?`TASK-021` 瀹告彃鐣剧拠顓濈疅
* 閺屻儴顕楁稉宥埿曢崣鎴濐吀閺?* 閺屻儴顕楁稉宥嗘暭閸?execution 閻樿埖鈧?* 閺屻儴顕楁稉宥呭晸 stage log
* `GET /api/v1/tasks/{taskId}/result` 鐎电懓顦荤捄顖氱窞娑撳秴褰?* `TASK-021` 閺冦垺婀?Controller 濞村鐦稉宥堫潶閻潙娼?* 閺傛澘顤冮張鈧亸蹇斿瘮娑斿懎瀵查弻銉嚄闁倿鍘ょ仦鍌涚ゴ鐠?* Docker Compose 閺嶅洤鍣悳顖氼暔濡偓閺屻儵鈧俺绻?* 娑撳秵鏌婃晶鐐存殶閹诡喖绨辨潻浣盒╅敍宀勬珟闂堢偛鍘涢弳鍌氫粻楠炴儼骞忓妤冣€樼拋?
## 閹笛嗩攽妞ゅ搫绨?
1. 缁楊兛绔撮梼鑸殿唽閿涙氨鍩楁禒璇插瀵ょ儤銆傞敍灞藉枙缂佹捁绔熼悾灞烩偓?2. 缁楊兛绨╅梼鑸殿唽閿涙odex 閹恒垺鐓￠悳鐗堟箒 V1 schema 娑撳骸鎮楃粩顖涘瘮娑斿懎瀵茬拋鍧楁６妞嬪孩鐗搁妴?3. 缁楊兛绗侀梼鑸殿唽閿涙odex 鐎圭偟骞囬張鈧亸蹇斿瘮娑斿懎瀵查弻銉嚄闁倿鍘ょ仦鍌樷偓?4. 缁楊剙娲撻梼鑸殿唽閿涙艾鐣鹃崥鎴炵ゴ鐠囨洑绗岃箛鍛邦洣閸ョ偛缍婇妴?5. 缁楊兛绨查梼鑸殿唽閿涙ocker Compose 閻滎垰顣ㄥΛ鈧弻銉ｂ偓?6. 缁楊剙鍙氶梼鑸殿唽閿涙碍褰佹禍銈嗘暪閸欙絻鈧?7. 缁楊兛绔烽梼鑸殿唽閿涙艾鍟€閸掋倖鏌囬弰顖氭儊闁倸鎮庨幏鍡欑舶 Claude Code / DeepSeek 鐏炩偓闁劍澧界悰灞烩偓?
瑜版挸澧犲鎻掔暚閹存劗顑囨稉鈧梼鑸殿唽瀵ょ儤銆傞妴浣侯儑娴滃矂妯佸▓鍨赴閺屻儯鈧胶顑囨稉澶愭▉濞堝灚娓剁亸蹇撶杽閻滈绗岀粭顒€娲撻梼鑸殿唽鐎规艾鎮滃ù瀣槸/韫囧懓顩﹂崶鐐茬秺閿涘苯绶熼幓鎰唉閺€璺哄經閵?
## 閺嗗倸浠犻弶鈥叉

* 閸欐垹骞囬棁鈧憰浣规殶閹诡喖绨辨潻浣盒?* 閸欐垹骞囬棁鈧憰浣锋叏閺€?`PRD.md` 閹?`docs/ARCHITECTURE.md`
* Docker Compose 閻滎垰顣ㄥ鍌氱埗
* 閸欐垹骞囪箛鍛淬€忓鏇炲弳瀵倹顒為梼鐔峰灙閹存牕鐣弫纾嬬殶鎼达妇閮寸紒?* 娴犺濮熼懠鍐ㄦ纯閹碘晛鐫嶉崚鏉垮缁旑垱鍨ㄩ惇鐔风杽 Word 鐟欙絾鐎?* 闂団偓鐟?Claude Code / DeepSeek 娴犲鍙嗛悥鏈垫崲閸?
## 閺傚洦銆傞弴瀛樻煀鐟曚焦鐪?
* `CURRENT_CONTEXT.md`閿涙碍娲块弬棰佽礋閳ユ翻ASK-021 瀹告彃鐣幋鎰嫙閹绘劒姘﹂敍娑樼秼閸撳秳瀵岀痪鍨瀼閹诡澀璐?TASK-022 閻栨湹鎹㈤崝鈥崇紦濡楋絺鈧?* `changelog/2026-06.md`閿涙俺顔囪ぐ?TASK-022 瀵ょ儤銆傛禍瀣杽閿涘奔浜掗崣?TASK-021 鐎瑰本鍨氶幀浣哄Ц閹椒绔撮懛瀛樷偓褌鎱ㄥ?* `tasks/MVP_TASK_MAP.md`閿涙艾鎮撳?TASK-021 閻樿埖鈧椒绗?TASK-022 娴犺濮熺紓鏍у娇/娴犺濮熺捄顖滃殠
* 婵″倹婀佽箛鍛邦洣閿涘奔鎱ㄥ?`tasks/active/TASK-021-result-url-query-api.md` 娑擃厸鈧粌绶熼幓鎰唉閳ユ繆銆冩潻?
## 閹恒垺鐓＄紒鎾诡啈

* V1 schema 娑?`task`閵嗕梗execution`閵嗕梗task_stage_log`閵嗕梗review_result_snapshot` 閸у洤鍑＄€涙ê婀敍灞肩瑬 `review_result_snapshot` 瀹告彃鍙挎径鍥嚢閸欐牗顒滃蹇曠波閺嬫粌鎻╅悡褎澧嶉棁鈧惃鍕彠闁?JSONB 閸滃瞼澧楅張顒€鐡у▓鐐光偓?* 瑜版挸澧犳禒鎾崇氨瀹告彃绱╅崗?MyBatis starter 娑?PostgreSQL/Flyway 娓氭繆绂嗛敍灞肩稻鐏忔碍婀铏圭彌濮濓絽绱?JPA / MyBatis / Spring Data Repository 閺佺増宓佺拋鍧楁６鐏炲倶鈧?* 瑜版挸澧犻崥搴ｎ伂濞屸剝婀侀悳鐗堝灇閹镐椒绠欓崠鏍ㄧ叀鐠囥垺膩閸ф绱盩ASK-021 姒涙顓婚弻銉嚄閺夈儲绨禒宥勮礋 `InMemoryTaskResultStore`閵?* `ReviewResultSnapshot` 瀹告煡鈧俺绻?TASK-021 Controller 鎼村繐鍨崠鏍﹁礋 JSON閿涙稑婀崥灞藉瘶閸愬懎褰查柅姘崇箖 `ObjectMapper` 閸欏秴绨崚妤€瀵查崶鐐额唶瑜版洜琚崹瀣ㄢ偓?* 閸╄桨绨?`review_result_snapshot` 閻?JSONB 閸掓鎷?`task` 鐞涖劌鐡ㄩ崷銊︹偓褍鍨介弬顓ㄧ礉閸欘垯浜掗崷銊ょ瑝閺傛澘顤冮弫鐗堝祦鎼存捁绺肩粔鑽ゆ畱閹懎鍠屾稉瀣暚閹存劖娓剁亸蹇斿瘮娑斿懎瀵查弻銉嚄闁倿鍘ょ仦鍌樷偓?
## 閺堫剝鐤嗙€圭偟骞囩紒鎾寸亯

* 閺傛澘顤?`PersistentTaskResultStore`閿涘奔缍旀稉娲帛鐠?`TaskResultStore` 閹镐椒绠欓崠鏍ㄧ叀鐠囥垹鐤勯悳甯窗
  * 娴ｈ法鏁?`JdbcTemplate + ObjectMapper`
  * 閸欘亣顕伴崣?`task` 閸?`review_result_snapshot`
  * `review_result_snapshot` 鐠囪褰囬弶鈥叉閸ュ搫鐣炬稉?`task_id + superseded_by_execution_id IS NULL + created_at DESC`
* 娣囨繃瀵?`GET /api/v1/tasks/{taskId}/result` 鐎电懓顦荤捄顖氱窞娑撳秴褰夐妴?* 娣囨繃瀵?`TASK-021` 瀹告彃鐣剧拠顓濈疅娑撳秴褰夐敍?  * 瀹稿弶婀佺紒鎾寸亯 -> `200`
  * 娴犺濮熸稉宥呯摠閸?-> `404`
  * 娴犺濮熺€涙ê婀担鍡樼梾閺堝绮ㄩ弸婊冩彥閻?-> `409`
* 娣囨繄鏆€ `InMemoryTaskResultStore` 娴ｆ粈璐?MVP 閺堚偓鐏忓繘妫撮悳顖涚ゴ鐠囨洘娴涢煬顐礉娑撳秴鍟€娴ｆ粈璐熸妯款吇閹镐椒绠欓崠鏍ㄧ叀鐠囥垹鐤勯悳鑸偓?* 閺堫剝鐤嗛張顏冩叏閺€?`ResultComposer` 閺嶇绺鹃崥鍫熷灇闁槒绶敍灞炬弓娣囶喗鏁?`TaskExecutionStateMachine` 閺嶇绺鹃悩鑸碘偓浣界讣缁夊鈧槒绶妴?
## 閺堫剝鐤嗗ù瀣槸娑撳酣鐛欑拠?
* 鐎规艾鎮滃ù瀣槸閿?  * `gradle test --tests "*PersistentTaskResultStoreTest" --tests "*TaskResultQueryServiceTest" --tests "*TaskResultQueryControllerTest"` -> 闁俺绻?* 閸ョ偛缍婂ù瀣槸閿?  * `gradle test --tests "*MinimalReviewEngineTest" --tests "*ResultComposerTest" --tests "*TaskExecutionStateMachineTest" --tests "*TaskResultQueryServiceTest" --tests "*TaskResultQueryControllerTest" --tests "*PersistentTaskResultStoreTest"` -> 闁俺绻?* Docker Compose 閺嶅洤鍣悳顖氼暔濡偓閺屻儻绱?  * `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example ps` -> 3 娑擃亝婀囬崝鈥虫綆娑?`Up`
  * `docker compose -f deploy/compose/compose.yml --env-file deploy/env/.env.example exec postgres pg_isready -U cqcp -d cqcp` -> 闂団偓鐟曚焦褰侀弶鍐ㄦ倵婢跺秵鐗抽敍娑樼秼閸撳秳鍞惍浣哥杽閻滅増婀崣妤呮▎婵?
## 鏉堝湱鏅Λ鈧弻銉х波鐠?
* 閺堫亙鎱ㄩ弨瑙勬殶閹诡喖绨辨潻浣盒╅妴?* 閺堫亙鎱ㄩ弨?`PRD.md`閵?* 閺堫亙鎱ㄩ弨?`docs/ARCHITECTURE.md`閵?* 閺堫亣袝绾版澘澧犵粩顖樷偓?* 閺堫亙鎱ㄩ弨?Docker 闁板秶鐤嗛妴?* 閺堫亙鎱ㄩ弨?`TASK-021` 鐎电懓顦婚幒銉ュ經鐠侯垰绶為妴?* 閺堫亣袝閸欐垵顓搁弽闈╃礉閺堫亪鍣搁弬鐗堝⒔鐞涘瞼濮搁幀浣规簚閵?* 閺堫亝鏁奸崣?execution 閻樿埖鈧降鈧?* 閺堫亜鍟?stage log閵?
