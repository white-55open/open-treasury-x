# 鍏呭€煎叆璐﹀墠閾句笂纭妫€鏌ワ紙deposit-chain-confirmation锛変换鍔℃竻鍗?
## 1. common锛氭柊澧炰笟鍔￠敊璇爜

- [x] 1.1 鍦?`otx-common` 鐨?`BizErrorEnum` 鏈熬鏂板 3 涓敊璇爜锛堟瘡涓甫涓枃 Javadoc 璇存槑瑙﹀彂鍦烘櫙锛夛細`DEPOSIT_CHAIN_INFO_MISS`锛坈hainId/chainTxHash 缂哄け鎴?requiredConfirmations 闈炴硶锛夈€乣DEPOSIT_TX_NOT_CONFIRMED`锛堥摼涓婁氦鏄撴湭杈剧‘璁ゆ暟锛夈€乣DEPOSIT_CHAIN_QUERY_FAILED`锛堥摼涓婃煡璇㈠け璐ワ級
  - **娴嬭瘯瑕佹眰**锛氭柊澧?鎵╁睍 `BizErrorEnumTest`鈥斺€擿errorEnum_declaresDepositChainErrorCodes_withUniqueCode` 鏂█ 3 涓柊鏋氫妇瀛樺湪涓?code 涓庢灇涓惧悕涓€鑷淬€佸叏灞€鏃犻噸澶?code锛沗get_unknownCode_returnsNull` 淇濇寔鏃㈡湁鏂█涓嶅洖褰?  - **楠屾敹**锛歚mvnw.cmd -pl otx-common test` 閫氳繃

## 2. application锛氭柊澧?DepositRequestDTO

- [x] 2.1 鏂板缓 `io.github.open55.otx.application.deposit.dto.DepositRequestDTO extends ChangeAmountRequest`锛屾柊澧炲瓧娈碉細`chainId`锛圫tring锛屽繀濉級銆乣chainTxHash`锛圫tring锛屽繀濉級銆乣requiredConfirmations`锛圛nteger锛屽彲閫夛級銆乣tokenAddress`锛圫tring锛屽彲閫夛級锛涙墍鏈夊瓧娈典娇鐢ㄤ腑鏂囧琛?Javadoc锛堥伒瀹?config.yaml documentation 閾佸緥锛?  - **娴嬭瘯瑕佹眰**锛氭柊澧?`DepositRequestDTOTest`鈥斺€擿depositRequestDTO_inheritsChangeAmountFields_andCarriesChainFields` 鏂█缁ф壙瀛楁锛坲id/amount/bizNo/currency锛夐€忎紶姝ｅ父涓旈摼瀛楁鍙鍐?  - **楠屾敹**锛歚mvnw.cmd -pl otx-application -am test -Dtest=DepositRequestDTOTest` 閫氳繃

## 3. application锛氱‘璁ら椄闂ㄧ紪鎺掞紙鏍稿績锛?
- [x] 3.1 `DepositAppServiceImpl` 娉ㄥ叆 `ChainQueryPort`锛堟瀯閫犲櫒娉ㄥ叆锛夛紝`deposit()` 绛惧悕鏀逛负 `deposit(DepositRequestDTO request)`锛屽湪 `changeAmountWithFundFlow` 涔嬪墠鏂板绉佹湁鏂规硶 `assertChainEvidence(request)`锛坈hainId/chainTxHash 闈炵┖銆乺equiredConfirmations 涓虹┖鎴栨鏁帮紝鍚﹀垯鎶?`DEPOSIT_CHAIN_INFO_MISS`锛変笌 `assertChainConfirmed(request, chainQueryPort)`锛坮esolve 纭鏁帮細璇锋眰瑕嗙洊鍊间紭鍏堛€佸惁鍒欑敤 `Web3jProperties` 榛樿 12锛沗isConfirmed` 杩斿洖 false 鎶?`DEPOSIT_TX_NOT_CONFIRMED`锛涙崟鑾?`Web3jRpcException` 杞?`DEPOSIT_CHAIN_QUERY_FAILED`锛?  - **娴嬭瘯瑕佹眰**锛氭墿灞?`DepositAppServiceImplTest`锛宍@Nested` 鍒嗙粍銆岄摼纭闂搁棬銆嶁€斺€擿deposit_withConfirmedTx_creditsBalanceAndPostsJournal`锛坢ock isConfirmed=true锛屾柇瑷€ changeAmountWithFundFlow 涓?postJournal 琚皟鐢級锛沗deposit_withUnconfirmedTx_throwsTxNotConfirmed_andNoSideEffects`锛坢ock false锛屾柇瑷€鎶?`DEPOSIT_TX_NOT_CONFIRMED` 涓?verifyNever 璋冪敤 changeAmountWithFundFlow/postJournal锛夛紱`deposit_whenChainQueryFails_throwsChainQueryFailed`锛坢ock 鎶?Web3jRpcException锛屾柇瑷€ `DEPOSIT_CHAIN_QUERY_FAILED`锛夛紱`deposit_withMissingChainId_throwsChainInfoMiss`锛沗deposit_withMissingTxHash_throwsChainInfoMiss`锛沗deposit_withInvalidRequiredConfirmations_throwsChainInfoMiss`锛?/璐熸暟锛?  - **楠屾敹**锛歚mvnw.cmd -pl otx-application -am test -Dtest=DepositAppServiceImplTest` 閫氳繃

- [x] 3.2 纭鏁拌В鏋愶細`requiredConfirmations` 璇锋眰瑕嗙洊鍊间紭鍏堬紝鍚﹀垯浣跨敤閰嶇疆榛樿鍊硷紱`@Nested` 鍒嗙粍銆岀‘璁ゆ暟瑙ｆ瀽銆嶆祴璇曪細`deposit_withoutOverrideUsesDefaultConfirmations`锛堟柇瑷€ isConfirmed 鏀跺埌 12锛夈€乣deposit_withOverrideUsesRequestConfirmations`锛堟柇瑷€鏀跺埌 6锛?  - **娴嬭瘯瑕佹眰**锛氬悓涓婃墿灞?`DepositAppServiceImplTest`锛宮ock `ChainQueryPort` 鐢?ArgumentCaptor 鏂█纭鏁板弬鏁?  - **楠屾敹**锛歚mvnw.cmd -pl otx-application -am test -Dtest=DepositAppServiceImplTest` 閫氳繃

- [x] 3.3 `buildDepositJournalRequest` 閾惧瓧娈靛～鍏咃細`chainId`/`chainTxHash` 鍙栬嚜璇锋眰锛涚‘璁ら€氳繃鍚庤皟鐢ㄤ竴娆?`queryTxReceipt` 鍙?`blockNumber` 濉厖鍑瘉锛堝洖鎵х己澶辨椂 blockNumber 缃?null 浠嶅厑璁稿叆璐︼紝瑙?design R3锛夛紱`tokenAddress` 鍙栬嚜璇锋眰鍙€夊瓧娈碉紱绉佹湁鏂规硶閫昏緫鍔犱腑鏂囪鍐呮敞閲?  - **娴嬭瘯瑕佹眰**锛歚@Nested` 鍒嗙粍銆屽嚟璇侀摼瀛楁銆嶁€斺€擿deposit_withConfirmedTx_journalCarriesChainEvidence`锛圓rgumentCaptor 鎹曡幏 `PostJournalRequestDTO`锛屾柇瑷€ chainId/chainTxHash 绛変簬璇锋眰鍊笺€乥lockNumber 绛変簬 mock 鍥炴墽鍊硷級锛沗deposit_whenReceiptMissing_journalKeepsNullBlockNumber`
  - **楠屾敹**锛歚mvnw.cmd -pl otx-application -am test -Dtest=DepositAppServiceImplTest` 閫氳繃

- [x] 3.4 骞傜瓑鍥炲綊锛氱‘璁ら椄闂ㄦ墽琛屽悗杩涘叆鏃㈡湁骞傜瓑璺緞锛沗@Nested` 鍒嗙粍銆屽箓绛夈€嶁€斺€擿deposit_withDuplicateBizNo_returnsOriginalBizNoAndNoDoubleCredit`锛坢ock isConfirmed=true銆乧hangeAmountWithFundFlow 骞傜瓑杩斿洖鍘?bizNo锛屾柇瑷€浣欓涓庢祦姘翠笉閲嶅锛?  - **娴嬭瘯瑕佹眰**锛氭墿灞?`DepositAppServiceImplTest`锛屾祴璇曟暟鎹敤 `private static final` 鍏峰悕甯搁噺锛坄TEST_UID`銆乣TEST_CHAIN_ID`銆乣TEST_TX_HASH`銆乣AMOUNT_100`銆乣DEFAULT_CONFIRMATIONS_12`锛?  - **楠屾敹**锛歚mvnw.cmd -pl otx-application -am test` 鍏ㄩ噺閫氳繃锛堝惈鏃㈡湁鐢ㄤ緥涓嶅洖褰掞級

## 4. interface锛氭帶鍒跺櫒濂戠害璋冩暣

- [x] 4.1 `DepositAppService` 鎺ュ彛绛惧悕 `deposit(ChangeAmountRequest)` 鈫?`deposit(DepositRequestDTO)`锛沗DepositController.deposit()` 璇锋眰浣撳悓姝ユ敼涓?`DepositRequestDTO`
  - **娴嬭瘯瑕佹眰**锛氭洿鏂?`DepositControllerTest`鈥斺€擿deposit_withValidRequest_returnsBizNo` 鏀圭敤 `DepositRequestDTO` JSON 璇锋眰浣擄紙鍚?chainId/chainTxHash锛夛紝鏂█杩斿洖 `Result<String>` 涓?data 涓?bizNo锛涙柊澧?`deposit_withChainEvidenceJson_bindsToDepositRequestDTO` 鏂█ JSON 缁戝畾閾惧瓧娈?  - **楠屾敹**锛歚mvnw.cmd -pl otx-interface -am test` 閫氳繃

## 5. infrastructure + starter锛氶厤缃」

- [x] 5.1 `Web3jProperties` 鏂板 `requiredConfirmations` 瀛楁锛坕nt锛岄粯璁?12锛屼腑鏂?Javadoc锛氬厖鍊煎叆璐︽墍闇€瀹夊叏纭鏁帮級
  - **娴嬭瘯瑕佹眰**锛氭洿鏂?`Web3jPropertiesTest`鈥斺€擿web3jProperties_requiredConfirmations_defaultsTo12` 鏂█榛樿鍊硷紱`web3jProperties_requiredConfirmations_bindsCustomValue` 鏂█鑷畾涔夊€肩粦瀹?  - **楠屾敹**锛歚mvnw.cmd -pl otx-infrastructure -am test -Dtest=Web3jPropertiesTest` 閫氳繃

- [x] 5.2 `otx-starter/src/main/resources/application-dev.yaml` 鐨?`web3j:` 娈垫柊澧?`required-confirmations: 12`锛堟敞閲婅鏄庯細鍏呭€煎叆璐︽墍闇€瀹夊叏纭鏁帮紝璇锋眰绾?requiredConfirmations 鍙鐩栵級
  - **娴嬭瘯瑕佹眰**锛氭棤鐙珛娴嬭瘯锛堥厤缃姞杞界敱 6.1 楠岃瘉锛?  - **楠屾敹**锛歽aml 璇硶鏍￠獙锛坄mvnw.cmd -pl otx-starter -am package -DskipTests` 鍙紪璇戦€氳繃锛?
## 6. 鍏ㄩ噺楠岃瘉

- [x] 6.1 杩愯鍏ㄩ噺妯″潡娴嬭瘯楠岃瘉鏈彉鏇达細`mvnw.cmd -pl otx-interface,otx-application,otx-infrastructure -am test`锛岀‘璁ゆ柊澧炵敤渚嬪叏閮ㄩ€氳繃銆佹棦鏈夌敤渚嬮浂鍥炲綊
  - **娴嬭瘯瑕佹眰**锛氭墍鏈夋柊澧?淇敼娴嬭瘯绫诲潎鏈変腑鏂囩被 Javadoc銆佹瘡涓?@Test 鏂规硶鏈?`鍦烘櫙锛歚 Javadoc + 涓嫳鍙岃 `@DisplayName`銆乻nake_case 鏂规硶鍚嶃€佹棤榄旀硶鏁板瓧锛堥伒瀹?testing/spec.md锛?  - **楠屾敹**锛氬懡浠ら€€鍑虹爜 0锛宍DepositAppServiceImplTest` / `DepositControllerTest` / `Web3jPropertiesTest` / `BizErrorEnumTest` 鍏ㄩ儴閫氳繃

