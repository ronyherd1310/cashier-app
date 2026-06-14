package com.cashierapp.photocheckout.data.recognizer

import com.cashierapp.photocheckout.domain.image.ImageCropper
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.recognizer.CONFIDENCE_THRESHOLD
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.domain.recognizer.Recognizer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * R5 crop-and-verify decorator. Pass 1 itemizes the counter; pass 2 rechecks only
 * uncertain boxed detections against a narrowed active sub-catalog.
 */
public class TwoPassRecognizer(
    private val primary: Recognizer,
    private val verifier: Recognizer,
    private val cropper: ImageCropper,
    private val confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
    private val maxEscalations: Int = MAX_ESCALATIONS,
) : Recognizer {
    override suspend fun recognize(
        image: CapturedImage,
        catalog: List<CatalogItem>,
    ): Result<List<RecognizedItem>> {
        val passOne = primary.recognize(image, catalog).getOrElse { error -> return Result.failure(error) }
        val source = image.original ?: image
        val activeCatalog = catalog.filter(CatalogItem::active)
        val groupBySku = activeCatalog.groupBySkuInActiveConfusionGroups()
        val selected =
            passOne
                .withIndex()
                .filter { (_, item) -> item.boundingBox != null && item.needsVerify(groupBySku) }
                .sortedBy { (_, item) -> item.confidence }
                .take(maxEscalations)

        if (selected.isEmpty()) {
            return Result.success(passOne)
        }

        val verified =
            coroutineScope {
                selected
                    .map { indexed ->
                        async {
                            val item = indexed.value
                            val box = item.boundingBox ?: return@async indexed.index to item
                            val crop = cropper.crop(source, box) ?: return@async indexed.index to item
                            val subCatalog = item.candidateSubCatalog(activeCatalog, groupBySku)
                            val best = verifier.recognize(crop, subCatalog).getOrNull()?.bestFor(subCatalog)
                            indexed.index to item.merge(best)
                        }
                    }.awaitAll()
            }.toMap()

        return Result.success(passOne.mapIndexed { index, item -> verified[index] ?: item })
    }

    private fun RecognizedItem.needsVerify(groupBySku: Map<String, String>): Boolean =
        confidence < confidenceThreshold ||
            occluded ||
            sku == null ||
            sku in groupBySku

    private fun RecognizedItem.candidateSubCatalog(
        activeCatalog: List<CatalogItem>,
        groupBySku: Map<String, String>,
    ): List<CatalogItem> {
        if (sku == null) {
            return activeCatalog
        }

        val candidateSkus = linkedSetOf<String>()
        candidateSkus += sku
        candidateSkus += alternates
        val group = groupBySku[sku]
        if (group != null) {
            activeCatalog
                .filter { item -> item.confusionGroup == group }
                .mapTo(candidateSkus) { item -> item.sku }
        }
        return activeCatalog.filter { item -> item.sku in candidateSkus }
    }

    private fun RecognizedItem.merge(best: RecognizedItem?): RecognizedItem =
        if (best == null) {
            this
        } else {
            copy(
                sku = best.sku,
                confidence = best.confidence,
                alternates = best.alternates,
            )
        }

    private fun List<RecognizedItem>.bestFor(subCatalog: List<CatalogItem>): RecognizedItem? {
        val allowedSkus = subCatalog.mapTo(HashSet()) { item -> item.sku }
        return asSequence()
            .filter { item -> item.sku in allowedSkus }
            .maxByOrNull { item -> item.confidence }
    }

    private fun List<CatalogItem>.groupBySkuInActiveConfusionGroups(): Map<String, String> {
        val activeGroups =
            asSequence()
                .mapNotNull { item -> item.confusionGroup?.takeIf(String::isNotBlank) }
                .groupingBy { group -> group }
                .eachCount()
                .filterValues { count -> count >= 2 }
                .keys
        return asSequence()
            .filter { item -> item.confusionGroup in activeGroups }
            .associate { item -> item.sku to item.confusionGroup.orEmpty() }
    }
}
