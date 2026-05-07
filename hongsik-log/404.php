<?php
/**
 * 404 template.
 *
 * @package HongsikLog
 */

get_header();
?>

<section class="single-article">
	<header class="single-header">
		<h1 class="single-title"><?php esc_html_e( '페이지를 찾을 수 없습니다.', 'hongsik-log' ); ?></h1>
	</header>
	<div class="entry-content">
		<p><?php esc_html_e( '주소가 바뀌었거나 아직 작성되지 않은 글일 수 있습니다.', 'hongsik-log' ); ?></p>
		<p><a href="<?php echo esc_url( home_url( '/' ) ); ?>"><?php esc_html_e( '홈으로 돌아가기', 'hongsik-log' ); ?></a></p>
	</div>
</section>

<?php
get_footer();
