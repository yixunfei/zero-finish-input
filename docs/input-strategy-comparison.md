# Offline input strategy comparison

Sources checked 2026-09-08. This is a comparison of documented behavior and local
ZeroInput tests, not a claim about proprietary algorithms or measured competitor
accuracy. No competing input method was installed or used with personal text.

| Behavior | Public evidence | ZeroInput response |
| --- | --- | --- |
| Candidate navigation and fuzzy pinyin | [Microsoft Simplified Chinese IME](https://support.microsoft.com/en-us/windows/hardware/input-devices/microsoft-simplified-chinese-ime) documents candidate navigation, fuzzy/double pinyin and special modes. | Keep full/abbreviated/fuzzy pinyin; add continuous expanded browsing with bounded page retention. Double pinyin is not implemented in this change. |
| Expanded candidate display | [Sogou Windows changelog](https://pinyin.sogou.com/changelog.php), 16.3b (2026-04-11), describes down-arrow multiline candidates and configurable paging. Latest visible entry was 16.8 (2026-08-28). | Recycled multiline candidates with scroll-edge loading, explicit previous/next, and verified selection routes. |
| Vocabulary and ranking | Sogou 16.6 (2026-06-26) announces a typing-model update; 16.8 describes 97,908 characters. Details of ranking and offline availability are not established by that page. | Preserve native sentence generation and public frequency scoring. Unknown phrases can be assembled, revised and learned through encrypted personalization. No claim of equivalent dictionary coverage. |
| Correcting and undoing | [Gboard help](https://support.google.com/gboard/answer/7068415?hl=en&co=GENIE.Platform%3DAndroid) documents suggestions, adding dictionary words and Backspace undo of autocorrection. | Explicit segment undo and a last-word reselection action; experimental typo alternatives are opt-in. Original spelling remains editable. |
| Neural completion | That Gboard page limits Smart Compose to US English and gives separate availability restrictions for proofreading/suggestions. Microsoft distinguishes Bing cloud suggestions. | Do not infer Chinese offline support from English/cloud features. No cloud inference. Small-model screening is documented separately. |
| Private data | The compared help pages do not establish ZeroInput's encryption/session guarantees. | No networking permission, no native user dictionary, no personal reads/learning in restricted editors; generation-checked encrypted writes. |

ZeroInput's related-reading tier begins after primary native pages end. It uses
public syllables to propose alternate segmentation and one nearby initial/final;
it does not silently enable fuzzy-pinyin preferences for the primary ranking.
Adjacent-key swaps, repeated/missing letters and transpositions belong to the
separate full-keyboard experiment. Rime gives these derived spellings reduced
credibility. The feature does not record coordinates or touch history.

Remaining broader product gaps include double pinyin, handwriting, speech,
domain-specific vocabulary coverage, and validated neural next-word prediction.
They are not implied to be completed by this change. Progress and measured
regressions should be tracked with fixed public fixtures and identical settings.
