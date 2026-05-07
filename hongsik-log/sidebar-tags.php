<?php
/**
 * Tag list sidebar.
 *
 * @package HongsikLog
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}
?>
<aside class="tag-panel" aria-label="<?php esc_attr_e( 'Tag list', 'hongsik-log' ); ?>">
	<p class="section-label">TAG LIST</p>
	<?php hongsik_log_render_tags(); ?>
</aside>
