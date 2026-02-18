package no.nav.bidrag.behandling.transformers

// Helper function to normalize notat content for comparison
fun String.normalizeForComparison(): String {
    // Remove HTML tags
    val withoutTags = this.replace(Regex("<[^>]*>"), "")
    // Decode HTML entities
    val decoded =
        withoutTags
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#\\d+;"), " ")
    // Remove all whitespace
    return decoded.replace(Regex("\\s+"), "")
}
