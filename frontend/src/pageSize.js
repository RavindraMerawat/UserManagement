/**
 * How many records a page holds, everywhere in the project.
 *
 * <p>The office asked for 25 rows a page on every grid, and asked more than once:
 * each screen had grown its own number - 10 here, 20 there, 50 on another - so
 * fixing the ones that were noticed left the rest behind. One constant, imported
 * by every screen that pages, is what stops that happening again. The server
 * defaults to the same number, so a call that omits it gets the same page.</p>
 *
 * <p>This is for <b>grids and record lists</b>. The handful of places that fill a
 * dropdown load their whole list instead and deliberately do not use this: a
 * picker cut off at 25 would be a picker you cannot choose from.</p>
 */
export const PAGE_SIZE = 25
