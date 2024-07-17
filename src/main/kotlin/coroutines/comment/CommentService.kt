package coroutines.comment.commentservice

import coroutines.recipes.mapasync.mapAsync
import domain.comment.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class CommentService(
    private val commentRepository: CommentRepository,
    private val userService: UserService,
    private val commentFactory: CommentFactory
) {
    suspend fun addComment(
        token: String,
        collectionKey: String,
        body: AddComment
    ) {
        commentRepository.addComment(
            commentFactory.toCommentDocument(
                userId = userService.readUserId(token),
                collectionKey = collectionKey,
                body = body
            )
        )
    }

    suspend fun getComments(
        collectionKey: String
    ): CommentsCollection {
        return CommentsCollection(
            collectionKey = collectionKey,
            elements = commentRepository
                .getComments(collectionKey)
                .mapAsync {
                    CommentElement(
                        id = it._id,
                        collectionKey = it.collectionKey,
                        user = userService.findUserById(it.userId),
                        comment = it.comment,
                        date = it.date
                    )
                }
        )
    }
}
