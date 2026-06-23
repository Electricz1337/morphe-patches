package app.morphe.patches.instagram.patches.distractionFree

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.instagram.misc.instagramExtensionPatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.utility.instagram.JsonParserFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/instagram/hide/stories/FilterStoriesListPatch;"

private object ReelTypeEnumFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("ads_reel", "suggested_user", "suggested_user_reel")
)

private object ReelResponseItemFingerprint : Fingerprint (
    definingClass = "ReelResponseItem"
)

private object TrayFingerprint : JsonParserFingerprint(
    "tray",
    "hallpass_share_info"
)

private object GetBlockedStoryTypesFingerprint : Fingerprint (
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "<clinit>"
)

val filterStoriesListPatch = bytecodePatch {
    dependsOn(instagramExtensionPatch)

    execute {
        with(TrayFingerprint.match()) {
            method.apply {
                val storiesListAssignmentIndex = indexOfFirstInstructionOrThrow(matchIndex) {
                    opcode == Opcode.IPUT_OBJECT && getReference<FieldReference>()?.type == "Ljava/util/List;"
                }

                val storiesListRegister =
                    getInstruction<TwoRegisterInstruction>(storiesListAssignmentIndex).registerA

                val freeRegister = findFreeRegister(
                    storiesListAssignmentIndex,
                    storiesListRegister
                )

                val reelTypeFieldName = ReelResponseItemFingerprint.classDef.fields.first {
                    ReelTypeEnumFingerprint.matchAll().map { it.classDef.type }.contains(it.type)
                }.name

                addInstructionsAtControlFlowLabel(
                    storiesListAssignmentIndex,
                    """
                        const-string v$freeRegister, "$reelTypeFieldName"
                        invoke-static { v$storiesListRegister, v$freeRegister }, $EXTENSION_CLASS_DESCRIPTOR->removeSuggestedStories(Ljava/util/List;Ljava/lang/String;)Ljava/util/List;
                        move-result-object v$storiesListRegister
                    """
                )
            }
        }
    }
}

context(_: BytecodePatchContext)
private fun filterStories(vararg blockedStoryTypes: String) = GetBlockedStoryTypesFingerprint.method.apply {
    val returnIndex = indexOfFirstInstructionOrThrow(Opcode.RETURN_VOID)

    addInstructions(
        returnIndex,
        blockedStoryTypes.joinToString("\n") {
            """
                const-string v0, "$it"
                sget-object v1, $EXTENSION_CLASS_DESCRIPTOR->BLOCKED_STORY_TYPES:Ljava/util/Set;
                invoke-interface {v1, v0}, Ljava/util/Set;->add(Ljava/lang/Object;)Z
            """
        }
    )
}

context(_: BytecodePatchContext)
internal fun filterSuggestedStories() = filterStories(
    "suggested_user_reel",
    "suggested_user",
    "suggested_creator_reel"
)

context(_: BytecodePatchContext)
internal fun filterHighlightedStories() = filterStories(
    "highlight_rewind_reel"
)


context(_: BytecodePatchContext)
internal fun filterAllStories() {
    filterSuggestedStories()
    filterHighlightedStories()
    filterStories("user_reel")
}
