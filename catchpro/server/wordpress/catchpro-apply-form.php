<?php
/**
 * Plugin Name: CatchPro Apply Form
 * Description: CatchPro Pro 신청 폼과 관리자 저장소를 제공합니다.
 * Version: 1.0.4
 * Author: CatchPro
 */

if (!defined('ABSPATH')) {
    exit;
}

add_action('init', 'catchpro_apply_register_post_type');
add_shortcode('catchpro_apply_form', 'catchpro_apply_render_form');
add_action('admin_post_nopriv_catchpro_apply_submit', 'catchpro_apply_handle_submit');
add_action('admin_post_catchpro_apply_submit', 'catchpro_apply_handle_submit');
add_action('admin_post_catchpro_license_upsert', 'catchpro_apply_handle_license_upsert');
add_action('admin_post_catchpro_license_from_application', 'catchpro_apply_handle_license_from_application');
add_action('admin_menu', 'catchpro_apply_register_settings_page');
add_action('admin_init', 'catchpro_apply_register_settings');
add_action('admin_notices', 'catchpro_apply_license_notice');
add_action('add_meta_boxes_catchpro_application', 'catchpro_apply_register_application_license_box');
add_filter('body_class', 'catchpro_apply_body_class');

function catchpro_apply_register_post_type(): void
{
    register_post_type('catchpro_application', [
        'labels' => [
            'name' => 'CatchPro 신청',
            'singular_name' => 'CatchPro 신청',
            'menu_name' => 'CatchPro 신청',
            'add_new_item' => '신청 직접 추가',
            'edit_item' => '신청 내용 보기',
            'view_item' => '신청 내용 보기',
            'search_items' => '신청 검색',
            'not_found' => '신청 내역이 없습니다.',
        ],
        'public' => false,
        'show_ui' => true,
        'show_in_menu' => true,
        'menu_icon' => 'dashicons-location-alt',
        'capability_type' => 'post',
        'supports' => ['title', 'editor'],
    ]);
}

function catchpro_apply_field(string $key): string
{
    return isset($_POST[$key]) ? sanitize_text_field(wp_unslash($_POST[$key])) : '';
}

function catchpro_apply_textarea(string $key): string
{
    return isset($_POST[$key]) ? sanitize_textarea_field(wp_unslash($_POST[$key])) : '';
}

function catchpro_apply_body_class(array $classes): array
{
    $post = get_post();
    if ((is_page('catchpro-pro-apply')) || ($post && has_shortcode((string) $post->post_content, 'catchpro_apply_form'))) {
        $classes[] = 'catchpro-apply-page';
    }

    return array_values(array_unique($classes));
}

function catchpro_apply_register_settings_page(): void
{
    add_options_page(
        'CatchPro 신청 설정',
        'CatchPro 신청',
        'manage_options',
        'catchpro-apply-settings',
        'catchpro_apply_render_settings_page'
    );

    add_submenu_page(
        'edit.php?post_type=catchpro_application',
        'CatchPro 라이선스 관리',
        '라이선스 관리',
        'manage_options',
        'catchpro-license-manager',
        'catchpro_apply_render_license_manager_page'
    );
}

function catchpro_apply_register_settings(): void
{
    register_setting('catchpro_apply_settings', 'catchpro_kakao_url', [
        'type' => 'string',
        'sanitize_callback' => 'esc_url_raw',
        'default' => '',
    ]);
    register_setting('catchpro_apply_settings', 'catchpro_license_api_base', [
        'type' => 'string',
        'sanitize_callback' => 'esc_url_raw',
        'default' => '',
    ]);
    register_setting('catchpro_apply_settings', 'catchpro_license_admin_token', [
        'type' => 'string',
        'sanitize_callback' => 'sanitize_text_field',
        'default' => '',
    ]);
}

function catchpro_apply_render_settings_page(): void
{
    if (!current_user_can('manage_options')) {
        return;
    }
    ?>
    <div class="wrap">
        <h1>CatchPro 신청 설정</h1>
        <form method="post" action="options.php">
            <?php settings_fields('catchpro_apply_settings'); ?>
            <table class="form-table" role="presentation">
                <tr>
                    <th scope="row"><label for="catchpro_kakao_url">카카오톡 상담 URL</label></th>
                    <td>
                        <input
                            id="catchpro_kakao_url"
                            name="catchpro_kakao_url"
                            type="url"
                            class="regular-text"
                            value="<?php echo esc_attr((string) get_option('catchpro_kakao_url', '')); ?>"
                            placeholder="https://pf.kakao.com/... 또는 https://open.kakao.com/o/..."
                        >
                        <p class="description">카카오 채널 채팅 URL이나 오픈채팅 URL을 입력하면 신청 페이지의 카카오톡 상담 버튼이 활성화됩니다.</p>
                    </td>
                </tr>
                <tr>
                    <th scope="row"><label for="catchpro_license_api_base">라이선스 API 주소</label></th>
                    <td>
                        <input
                            id="catchpro_license_api_base"
                            name="catchpro_license_api_base"
                            type="url"
                            class="regular-text"
                            value="<?php echo esc_attr((string) get_option('catchpro_license_api_base', '')); ?>"
                            placeholder="https://hongsik.blog"
                        >
                        <p class="description">비워두면 현재 사이트 주소를 사용합니다. 예: https://hongsik.blog</p>
                    </td>
                </tr>
                <tr>
                    <th scope="row"><label for="catchpro_license_admin_token">라이선스 관리자 토큰</label></th>
                    <td>
                        <input
                            id="catchpro_license_admin_token"
                            name="catchpro_license_admin_token"
                            type="password"
                            class="regular-text"
                            value="<?php echo esc_attr((string) get_option('catchpro_license_admin_token', '')); ?>"
                            autocomplete="new-password"
                        >
                        <p class="description">서버의 CATCHPRO_LICENSE_ADMIN_TOKEN 값과 같아야 라이선스 등록/연장이 가능합니다.</p>
                    </td>
                </tr>
            </table>
            <?php submit_button('설정 저장'); ?>
        </form>
    </div>
    <?php
}

function catchpro_apply_get_kakao_url(array $atts): string
{
    $shortcode_url = isset($atts['kakao_url']) ? esc_url_raw((string) $atts['kakao_url']) : '';
    if ($shortcode_url !== '') {
        return $shortcode_url;
    }

    $option_url = get_option('catchpro_kakao_url', '');
    return is_string($option_url) ? esc_url_raw($option_url) : '';
}

function catchpro_apply_license_api_base(): string
{
    $base = get_option('catchpro_license_api_base', '');
    if (!is_string($base) || trim($base) === '') {
        $base = home_url();
    }
    return rtrim(esc_url_raw($base), '/');
}

function catchpro_apply_license_admin_token(): string
{
    $token = get_option('catchpro_license_admin_token', '');
    return is_string($token) ? trim($token) : '';
}

function catchpro_apply_license_request(string $method, string $path, array $body = [])
{
    $token = catchpro_apply_license_admin_token();
    if ($token === '') {
        return new WP_Error('catchpro_license_token_missing', '라이선스 관리자 토큰이 설정되지 않았습니다.');
    }

    $args = [
        'method' => strtoupper($method),
        'timeout' => 12,
        'headers' => [
            'Accept' => 'application/json',
            'Content-Type' => 'application/json; charset=utf-8',
            'X-CatchPro-Admin-Token' => $token,
        ],
    ];
    if ($body !== []) {
        $args['body'] = wp_json_encode($body);
    }

    $response = wp_remote_request(catchpro_apply_license_api_base() . $path, $args);
    if (is_wp_error($response)) {
        return $response;
    }

    $status = (int) wp_remote_retrieve_response_code($response);
    $decoded = json_decode((string) wp_remote_retrieve_body($response), true);
    if (!is_array($decoded)) {
        return new WP_Error('catchpro_license_bad_response', '라이선스 서버 응답을 해석하지 못했습니다.');
    }
    if ($status < 200 || $status >= 300 || empty($decoded['ok'])) {
        $message = isset($decoded['error']) ? (string) $decoded['error'] : '라이선스 서버 요청이 실패했습니다.';
        return new WP_Error('catchpro_license_api_failed', $message);
    }

    return $decoded;
}

function catchpro_apply_license_notice(): void
{
    if (!current_user_can('manage_options')) {
        return;
    }
    $notice = isset($_GET['catchpro_license_notice']) ? sanitize_text_field(wp_unslash($_GET['catchpro_license_notice'])) : '';
    if ($notice === '') {
        return;
    }
    $messages = [
        'saved' => '라이선스를 저장했습니다.',
        'extended' => '라이선스를 연장했습니다.',
        'expired' => '라이선스를 만료 처리했습니다.',
        'blocked' => '라이선스를 차단했습니다.',
        'device_reset' => '등록 기기를 초기화했습니다.',
    ];
    $message = $messages[$notice] ?? '라이선스 작업을 완료했습니다.';
    echo '<div class="notice notice-success is-dismissible"><p>' . esc_html($message) . '</p></div>';
}

function catchpro_apply_license_error_notice(WP_Error $error): void
{
    echo '<div class="notice notice-error"><p>' . esc_html($error->get_error_message()) . '</p></div>';
}

function catchpro_apply_license_status_label(string $status): string
{
    $labels = [
        'trial' => '무료체험',
        'active' => '정상구독',
        'past_due' => '결제유예',
        'expired' => '만료',
        'blocked' => '차단',
        'device_change_pending' => '기기변경대기',
    ];
    return $labels[$status] ?? $status;
}

function catchpro_apply_license_product_code(): string
{
    return 'catchpro-pro';
}

function catchpro_apply_license_product_label(): string
{
    return 'CatchPro Pro 구독';
}

function catchpro_apply_register_application_license_box(): void
{
    add_meta_box(
        'catchpro-application-license',
        '라이선스 등록',
        'catchpro_apply_render_application_license_box',
        'catchpro_application',
        'side',
        'high'
    );
}

function catchpro_apply_render_application_license_box(WP_Post $post): void
{
    if (!current_user_can('manage_options')) {
        return;
    }
    $name = (string) get_post_meta($post->ID, 'catchpro_name', true);
    $phone = (string) get_post_meta($post->ID, 'catchpro_phone', true);
    $email = (string) get_post_meta($post->ID, 'catchpro_email', true);
    $registered = (string) get_post_meta($post->ID, 'catchpro_license_registered_editions', true);
    ?>
    <p>신청 정보로 통합 Pro 구독 1개월 체험을 등록합니다.</p>
    <p>
        <strong><?php echo esc_html($name !== '' ? $name : '이름 없음'); ?></strong><br>
        <?php echo esc_html($phone !== '' ? $phone : '전화번호 없음'); ?><br>
        <?php echo esc_html($email !== '' ? $email : '이메일 없음'); ?>
    </p>
    <?php if ($registered !== '') : ?>
        <p><strong>최근 등록:</strong> <?php echo esc_html($registered); ?></p>
    <?php endif; ?>
    <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>" style="display:grid; gap:8px;">
        <?php wp_nonce_field('catchpro_license_from_application', 'catchpro_license_nonce'); ?>
        <input type="hidden" name="action" value="catchpro_license_from_application">
        <input type="hidden" name="post_id" value="<?php echo esc_attr((string) $post->ID); ?>">
        <button class="button button-primary" type="submit" name="edition_mode" value="<?php echo esc_attr(catchpro_apply_license_product_code()); ?>">CatchPro Pro 1개월 체험</button>
    </form>
    <p class="description">등록 후 고객은 앱 설정 탭에서 같은 이메일/전화번호로 라이선스 확인을 누르면 됩니다.</p>
    <?php
}

function catchpro_apply_render_license_manager_page(): void
{
    if (!current_user_can('manage_options')) {
        return;
    }
    ?>
    <div class="wrap">
        <h1>CatchPro 라이선스 관리</h1>
        <p>고객별 통합 Pro 구독 상태와 만료일을 관리합니다. 하나의 구독으로 인성 CatchPro Pro와 CatchPro Navi Pro 사용 권한을 함께 관리합니다.</p>

        <div class="card" style="max-width: 960px; padding: 18px;">
            <h2 style="margin-top:0;">작업 설명</h2>
            <ul style="list-style:disc; padding-left:20px;">
                <li><strong>1개월 연장</strong>: 현재 만료일이 남아 있으면 그 날짜에서 30일을 더하고, 이미 만료됐으면 오늘부터 30일을 새로 부여합니다. 유료 결제 확인 후 사용하는 작업입니다.</li>
                <li><strong>체험 30일</strong>: 상태를 무료체험으로 바꾸고 30일 이용 기간을 부여합니다. 신규 상담 고객에게 첫 달 무료체험을 열 때 사용합니다.</li>
                <li><strong>기기 초기화</strong>: 등록된 기기값을 지웁니다. 기본은 고객 1명당 최대 2대까지 자동 등록되며, 고객이 휴대폰을 바꿨거나 잘못된 기기에 묶였을 때 사용합니다.</li>
                <li><strong>만료 처리</strong>: 구독 상태를 만료로 바꾸고 만료일을 현재 시각으로 저장합니다. 기간 종료나 미결제 고객을 정리할 때 사용합니다.</li>
                <li><strong>차단</strong>: 구독 상태를 차단으로 바꿉니다. 환불/오남용/지원 중단 등 즉시 사용을 막아야 할 때 사용합니다.</li>
            </ul>
        </div>

        <h2>빠른 등록</h2>
        <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>" class="card" style="max-width: 960px; padding: 18px;">
            <?php wp_nonce_field('catchpro_license_upsert', 'catchpro_license_nonce'); ?>
            <input type="hidden" name="action" value="catchpro_license_upsert">
            <input type="hidden" name="license_action" value="save">
            <table class="form-table" role="presentation">
                <tr>
                    <th scope="row"><label for="catchpro_license_name">이름</label></th>
                    <td><input id="catchpro_license_name" name="name" type="text" class="regular-text"></td>
                </tr>
                <tr>
                    <th scope="row"><label for="catchpro_license_phone">전화번호</label></th>
                    <td><input id="catchpro_license_phone" name="phone" type="text" class="regular-text" placeholder="01012345678"></td>
                </tr>
                <tr>
                    <th scope="row"><label for="catchpro_license_email">이메일</label></th>
                    <td><input id="catchpro_license_email" name="email" type="email" class="regular-text"></td>
                </tr>
                <tr>
                    <th scope="row">상품</th>
                    <td>
                        <strong><?php echo esc_html(catchpro_apply_license_product_label()); ?></strong>
                        <input type="hidden" name="edition" value="<?php echo esc_attr(catchpro_apply_license_product_code()); ?>">
                    </td>
                </tr>
                <tr>
                    <th scope="row"><label for="catchpro_license_status">상태</label></th>
                    <td>
                        <select id="catchpro_license_status" name="status">
                            <option value="trial">무료체험</option>
                            <option value="active">정상구독</option>
                            <option value="past_due">결제유예</option>
                            <option value="expired">만료</option>
                            <option value="blocked">차단</option>
                        </select>
                        <select name="extend_days">
                            <option value="30">30일</option>
                            <option value="7">7일</option>
                            <option value="31">31일</option>
                            <option value="0">만료일 유지</option>
                        </select>
                    </td>
                </tr>
                <tr>
                    <th scope="row"><label for="catchpro_license_memo">메모</label></th>
                    <td><input id="catchpro_license_memo" name="memo" type="text" class="large-text" placeholder="예: 1개월 무료체험"></td>
                </tr>
            </table>
            <?php submit_button('라이선스 등록/갱신'); ?>
        </form>

        <h2>라이선스 목록</h2>
        <?php
        $result = catchpro_apply_license_request('GET', '/api/license/list');
        if (is_wp_error($result)) {
            catchpro_apply_license_error_notice($result);
            echo '<p>설정 > CatchPro 신청에서 라이선스 API 주소와 관리자 토큰을 확인하세요.</p>';
        } else {
            $licenses = isset($result['licenses']) && is_array($result['licenses']) ? $result['licenses'] : [];
            if ($licenses === []) {
                echo '<p>등록된 라이선스가 없습니다.</p>';
            } else {
                ?>
                <table class="widefat striped">
                    <thead>
                    <tr>
                        <th>고객</th>
                        <th>상품</th>
                        <th>상태</th>
                        <th>만료일</th>
                        <th>기기</th>
                        <th>메모</th>
                        <th>작업</th>
                    </tr>
                    </thead>
                    <tbody>
                    <?php foreach ($licenses as $license) :
                        $status = sanitize_text_field((string) ($license['status'] ?? ''));
                        $days_remaining = $license['daysRemaining'] ?? null;
                        ?>
                        <tr>
                            <td>
                                <strong><?php echo esc_html((string) ($license['name'] ?? '-')); ?></strong><br>
                                <?php echo esc_html((string) ($license['phone'] ?? '-')); ?><br>
                                <?php echo esc_html((string) ($license['email'] ?? '-')); ?>
                            </td>
                            <td><?php echo esc_html(catchpro_apply_license_product_label()); ?></td>
                            <td><?php echo esc_html(catchpro_apply_license_status_label($status)); ?></td>
                            <td>
                                <?php echo esc_html((string) ($license['expiresAt'] ?? '-')); ?><br>
                                <span class="description">
                                    <?php echo is_numeric($days_remaining) ? esc_html($days_remaining . '일 남음') : '만료일 없음'; ?>
                                </span>
                            </td>
                            <td>
                                <?php
                                $device_count = (int) ($license['deviceCount'] ?? 0);
                                $max_devices = (int) ($license['maxDevices'] ?? 2);
                                echo esc_html($device_count > 0 ? '등록됨 ' . $device_count . '/' . $max_devices . '대' : '미등록');
                                if (!empty($license['deviceIdSuffix'])) {
                                    echo '<br><span class="description">' . esc_html((string) $license['deviceIdSuffix']) . '</span>';
                                }
                                ?>
                            </td>
                            <td><?php echo esc_html((string) ($license['memo'] ?? '')); ?></td>
                            <td>
                                <div style="display:flex; flex-wrap:wrap; gap:6px;">
                                    <?php
                                    catchpro_apply_license_row_button($license, 'extend_30', '1개월 연장', 'button-primary');
                                    catchpro_apply_license_row_button($license, 'trial_30', '체험 30일');
                                    catchpro_apply_license_row_button($license, 'reset_device', '기기 초기화');
                                    catchpro_apply_license_row_button($license, 'expire', '만료 처리');
                                    catchpro_apply_license_row_button($license, 'block', '차단');
                                    ?>
                                </div>
                            </td>
                        </tr>
                    <?php endforeach; ?>
                    </tbody>
                </table>
                <?php
            }
        }
        ?>
    </div>
    <?php
}

function catchpro_apply_license_row_button(array $license, string $license_action, string $label, string $class = ''): void
{
    $button_class = trim('button ' . $class);
    ?>
    <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
        <?php wp_nonce_field('catchpro_license_upsert', 'catchpro_license_nonce'); ?>
        <input type="hidden" name="action" value="catchpro_license_upsert">
        <input type="hidden" name="license_action" value="<?php echo esc_attr($license_action); ?>">
        <input type="hidden" name="name" value="<?php echo esc_attr((string) ($license['name'] ?? '')); ?>">
        <input type="hidden" name="phone" value="<?php echo esc_attr((string) ($license['phone'] ?? '')); ?>">
        <input type="hidden" name="email" value="<?php echo esc_attr((string) ($license['email'] ?? '')); ?>">
        <input type="hidden" name="edition" value="<?php echo esc_attr(catchpro_apply_license_product_code()); ?>">
        <input type="hidden" name="status" value="<?php echo esc_attr((string) ($license['status'] ?? 'active')); ?>">
        <input type="hidden" name="memo" value="<?php echo esc_attr((string) ($license['memo'] ?? '')); ?>">
        <button class="<?php echo esc_attr($button_class); ?>" type="submit"><?php echo esc_html($label); ?></button>
    </form>
    <?php
}

function catchpro_apply_render_form($raw_atts = []): string
{
    $atts = shortcode_atts([
        'kakao_url' => '',
    ], is_array($raw_atts) ? $raw_atts : [], 'catchpro_apply_form');

    $is_done = isset($_GET['catchpro_apply']) && $_GET['catchpro_apply'] === 'success';
    $kakao_url = catchpro_apply_get_kakao_url($atts);
    $has_kakao = $kakao_url !== '';

    ob_start();
    ?>
    <script>
        document.documentElement.classList.add('catchpro-js');
        document.body.classList.add('catchpro-apply-page');
    </script>
    <style>
        @import url("https://cdnjs.cloudflare.com/ajax/libs/pretendard/1.3.9/variable/pretendardvariable.css");

        .site-shell,
        body.catchpro-apply-page .site-shell {
            display: block !important;
            max-width: none !important;
            width: 100% !important;
            margin: 0 !important;
        }
        .site-header,
        .single-header,
        .site-footer,
        body.catchpro-apply-page .site-header,
        body.catchpro-apply-page .single-header,
        body.catchpro-apply-page .site-footer {
            display: none !important;
        }
        #content,
        .single-article,
        .entry-content,
        body.catchpro-apply-page #content,
        body.catchpro-apply-page .single-article,
        body.catchpro-apply-page .entry-content {
            max-width: none !important;
            width: 100% !important;
            margin: 0 !important;
            padding: 0 !important;
        }
        .entry-content > *,
        body.catchpro-apply-page .entry-content > * {
            max-width: none !important;
        }
        .catchpro-apply {
            --cp-ink: #15191f;
            --cp-muted: #5d6472;
            --cp-line: #dfe3ea;
            --cp-soft: #f7f8fb;
            --cp-blue: #6f73ff;
            --cp-blue-dark: #5459e8;
            --cp-blue-soft: #f2f3ff;
            --cp-blue-line: #d9dcff;
            --cp-green: #158f72;
            --cp-radius: 8px;
            color: var(--cp-ink);
            font-family: "Pretendard Variable", "Pretendard", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            line-height: 1.65;
            background: #fff;
        }
        .catchpro-apply * {
            box-sizing: border-box;
        }
        @keyframes catchproFadeUp {
            from {
                opacity: 0;
                transform: translateY(14px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
        @keyframes catchproFadeIn {
            from {
                opacity: 0;
            }
            to {
                opacity: 1;
            }
        }
        .catchpro-apply__hero {
            background: #fff;
            color: var(--cp-ink);
            border-bottom: 1px solid var(--cp-line);
            margin: 0 calc(50% - 50vw) 0;
            padding: 64px max(24px, calc((100vw - 1040px) / 2)) 52px;
        }
        .catchpro-apply__eyebrow {
            display: inline-flex;
            align-items: center;
            gap: 10px;
            margin: 0 0 14px;
            color: var(--cp-blue);
            font-size: 18px;
            font-weight: 900;
            letter-spacing: 0;
            opacity: 0;
            animation: catchproFadeUp 0.55s ease forwards;
        }
        .catchpro-apply__brand-mark {
            position: relative;
            display: inline-flex;
            width: 34px;
            height: 34px;
            flex: 0 0 34px;
            border-radius: 11px;
            background: linear-gradient(135deg, #8e90ff 0%, #6f73ff 50%, #b4a7ff 100%);
            box-shadow: 0 10px 20px rgba(111, 115, 255, 0.22);
        }
        .catchpro-apply__brand-mark::before {
            content: "";
            position: absolute;
            left: 10px;
            top: 8px;
            width: 14px;
            height: 18px;
            border-radius: 4px 11px 11px 4px;
            background: rgba(255, 255, 255, 0.92);
            transform: skewX(-14deg);
        }
        .catchpro-apply__brand-mark::after {
            content: "";
            position: absolute;
            right: 7px;
            bottom: 7px;
            width: 9px;
            height: 9px;
            border-right: 3px solid rgba(255, 255, 255, 0.95);
            border-bottom: 3px solid rgba(255, 255, 255, 0.95);
            transform: rotate(-45deg);
        }
        .catchpro-apply__hero h1 {
            max-width: 780px;
            margin: 0;
            color: var(--cp-ink);
            font-size: clamp(34px, 5vw, 58px);
            line-height: 1.12;
            letter-spacing: 0;
            opacity: 0;
            animation: catchproFadeUp 0.62s ease 0.08s forwards;
        }
        .catchpro-apply__lead {
            max-width: 720px;
            margin: 22px 0 0;
            color: var(--cp-muted);
            font-size: 19px;
            opacity: 0;
            animation: catchproFadeUp 0.62s ease 0.16s forwards;
        }
        .catchpro-apply__actions {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            margin-top: 30px;
            opacity: 0;
            animation: catchproFadeUp 0.62s ease 0.24s forwards;
        }
        .catchpro-apply__button {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-height: 46px;
            padding: 0 18px;
            border-radius: var(--cp-radius);
            background: var(--cp-blue);
            color: #fff;
            font-weight: 800;
            text-decoration: none;
            transition: transform 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease, border-color 0.18s ease;
        }
        .catchpro-apply__button:hover {
            transform: translateY(-1px);
            box-shadow: 0 8px 18px rgba(111, 115, 255, 0.18);
        }
        .catchpro-apply__button--ghost {
            background: #fff;
            color: var(--cp-blue);
            border: 1px solid var(--cp-blue-line);
        }
        .catchpro-apply__button--kakao {
            background: #fee500;
            color: #1d1d1d;
        }
        .catchpro-apply__button--disabled {
            background: #f2f4f8;
            color: #717888;
            border: 1px solid var(--cp-line);
            cursor: default;
        }
        .catchpro-apply__button--disabled:hover {
            transform: none;
            box-shadow: none;
        }
        .catchpro-apply__wrap {
            max-width: 1040px;
            margin: 0 auto;
            padding: 42px 0 64px;
        }
        .catchpro-apply__notice {
            border: 1px solid #b8e2cc;
            border-left: 5px solid var(--cp-green);
            border-radius: var(--cp-radius);
            padding: 16px 18px;
            background: #f5fbf8;
            color: #194932;
            font-weight: 700;
            margin-bottom: 22px;
            animation: catchproFadeIn 0.4s ease both;
        }
        .catchpro-apply__grid {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, 1fr));
            gap: 16px;
            margin: 28px 0 36px;
        }
        .catchpro-apply__panel {
            border: 1px solid var(--cp-line);
            border-radius: var(--cp-radius);
            padding: 22px;
            background: #fff;
            box-shadow: none;
            opacity: 1;
            transform: none;
            transition: opacity 0.5s ease, transform 0.5s ease, border-color 0.18s ease, background-color 0.18s ease;
        }
        .catchpro-js .catchpro-apply__panel {
            opacity: 0;
            transform: translateY(24px);
        }
        .catchpro-js .catchpro-apply__panel.is-visible {
            opacity: 1;
            transform: translateY(0);
        }
        .catchpro-js .catchpro-apply__panel:nth-child(2) {
            transition-delay: 0.08s;
        }
        .catchpro-js .catchpro-apply__panel:nth-child(3) {
            transition-delay: 0.16s;
        }
        .catchpro-apply__panel:hover {
            transform: translateY(-2px);
            border-color: var(--cp-blue-line);
            background: #fcfdff;
        }
        .catchpro-apply__panel h2,
        .catchpro-apply__panel h3 {
            margin: 0 0 10px;
            font-size: 22px;
            line-height: 1.32;
            letter-spacing: 0;
        }
        .catchpro-apply__panel p {
            margin: 0;
            color: var(--cp-muted);
            font-size: 16px;
            line-height: 1.72;
        }
        .catchpro-apply__feature-label {
            display: inline-flex;
            align-items: center;
            min-height: 26px;
            margin-bottom: 12px;
            padding: 0 10px;
            border: 1px solid rgba(111, 115, 255, 0.22);
            border-radius: 999px;
            background: var(--cp-blue-soft);
            color: var(--cp-blue);
            font-size: 13px;
            font-weight: 900;
        }
        .catchpro-apply__accent {
            color: var(--cp-blue);
            font-weight: 900;
        }
        .catchpro-apply__section-title {
            margin: 0 0 10px;
            font-size: 28px;
            letter-spacing: 0;
        }
        .catchpro-apply__section-copy {
            max-width: 760px;
            margin: 0 0 22px;
            color: var(--cp-muted);
        }
        .catchpro-apply__flow {
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 1px;
            overflow: hidden;
            border: 1px solid var(--cp-line);
            border-radius: var(--cp-radius);
            background: var(--cp-line);
            margin-bottom: 40px;
        }
        .catchpro-apply__step {
            background: #fff;
            padding: 18px;
            opacity: 0;
            animation: catchproFadeUp 0.5s ease forwards;
        }
        .catchpro-apply__step:nth-child(2) {
            animation-delay: 0.05s;
        }
        .catchpro-apply__step:nth-child(3) {
            animation-delay: 0.1s;
        }
        .catchpro-apply__step:nth-child(4) {
            animation-delay: 0.15s;
        }
        .catchpro-apply__step strong {
            display: block;
            margin-bottom: 6px;
            color: var(--cp-blue);
        }
        .catchpro-apply__form {
            border: 1px solid var(--cp-line);
            border-radius: var(--cp-radius);
            background: #fff;
            padding: 28px;
            box-shadow: none;
            opacity: 0;
            animation: catchproFadeUp 0.55s ease 0.08s forwards;
        }
        .catchpro-apply__fields {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 16px;
        }
        .catchpro-apply__field {
            display: flex;
            flex-direction: column;
            gap: 7px;
        }
        .catchpro-apply__field--wide {
            grid-column: 1 / -1;
        }
        .catchpro-apply label {
            font-weight: 800;
            color: #242a33;
        }
        .catchpro-apply input,
        .catchpro-apply select,
        .catchpro-apply textarea {
            width: 100%;
            min-height: 46px;
            border: 1px solid #cfd6df;
            border-radius: var(--cp-radius);
            padding: 10px 12px;
            color: var(--cp-ink);
            background: #fff;
            font: inherit;
            transition: border-color 0.16s ease, box-shadow 0.16s ease, background-color 0.16s ease;
        }
        .catchpro-apply textarea {
            min-height: 120px;
            resize: vertical;
        }
        .catchpro-apply input:focus,
        .catchpro-apply select:focus,
        .catchpro-apply textarea:focus {
            outline: 3px solid rgba(111, 115, 255, 0.16);
            border-color: var(--cp-blue);
        }
        .catchpro-apply__consent {
            display: flex;
            gap: 10px;
            align-items: flex-start;
            margin: 18px 0 22px;
            color: var(--cp-muted);
            font-size: 15px;
        }
        .catchpro-apply__consent input {
            width: 20px;
            min-height: 20px;
            margin-top: 3px;
        }
        .catchpro-apply__submit {
            width: 100%;
            min-height: 52px;
            border: 0;
            border-radius: var(--cp-radius);
            background: var(--cp-blue);
            color: #fff;
            cursor: pointer;
            font: inherit;
            font-size: 17px;
            font-weight: 900;
            transition: transform 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
        }
        .catchpro-apply__submit:hover {
            background: var(--cp-blue-dark);
            transform: translateY(-1px);
            box-shadow: 0 10px 20px rgba(111, 115, 255, 0.2);
        }
        .catchpro-apply__contact {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            gap: 14px;
            align-items: center;
            margin: 24px 0;
            padding: 18px;
            border: 1px solid #f0de5b;
            border-radius: var(--cp-radius);
            background: #fffdf0;
            opacity: 0;
            animation: catchproFadeUp 0.5s ease forwards;
        }
        .catchpro-apply__contact strong {
            display: block;
            margin-bottom: 4px;
            color: #2b2610;
        }
        .catchpro-apply__contact span {
            color: #665d35;
            font-size: 15px;
        }
        .catchpro-apply__contact-link {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-height: 44px;
            padding: 0 16px;
            border-radius: var(--cp-radius);
            background: #fee500;
            color: #1d1d1d;
            font-weight: 900;
            text-decoration: none;
            white-space: nowrap;
            transition: transform 0.18s ease, box-shadow 0.18s ease;
        }
        .catchpro-apply__contact-link:hover {
            transform: translateY(-1px);
            box-shadow: 0 8px 16px rgba(150, 124, 0, 0.16);
        }
        .catchpro-apply__contact-link--disabled {
            background: #e6e0bd;
            color: #706941;
            cursor: default;
        }
        .catchpro-apply__contact-link--disabled:hover {
            transform: none;
            box-shadow: none;
        }
        .catchpro-apply__fine {
            margin: 14px 0 0;
            color: var(--cp-muted);
            font-size: 14px;
        }
        .catchpro-apply__footer {
            margin: 42px 0 0;
            padding: 22px 0 0;
            border-top: 1px solid var(--cp-line);
            color: var(--cp-muted);
            font-size: 14px;
        }
        .catchpro-apply__footer strong {
            color: var(--cp-ink);
            font-weight: 900;
        }
        .catchpro-apply__footer a {
            color: var(--cp-blue);
            font-weight: 800;
            text-decoration: none;
        }
        .catchpro-apply__hidden {
            display: none;
        }
        @media (max-width: 820px) {
            .catchpro-apply__hero {
                padding-top: 54px;
                padding-bottom: 48px;
            }
            .catchpro-apply__grid,
            .catchpro-apply__flow,
            .catchpro-apply__fields,
            .catchpro-apply__contact {
                grid-template-columns: 1fr;
            }
        }
        @media (prefers-reduced-motion: reduce) {
            .catchpro-apply *,
            .catchpro-apply *::before,
            .catchpro-apply *::after {
                animation-duration: 0.01ms !important;
                animation-iteration-count: 1 !important;
                scroll-behavior: auto !important;
                transition-duration: 0.01ms !important;
            }
            .catchpro-apply__eyebrow,
            .catchpro-apply__hero h1,
            .catchpro-apply__lead,
            .catchpro-apply__actions,
            .catchpro-apply__panel,
            .catchpro-apply__step,
            .catchpro-apply__form,
            .catchpro-apply__contact {
                opacity: 1 !important;
                transform: none !important;
            }
        }
    </style>
    <div class="catchpro-apply">
        <section class="catchpro-apply__hero">
            <p class="catchpro-apply__eyebrow"><span class="catchpro-apply__brand-mark" aria-hidden="true"></span><span>CatchPro Pro</span></p>
            <h1>운행에 필요한 기능만 골라 더 빠르게 시작하세요.</h1>
            <p class="catchpro-apply__lead">
                CatchPro Pro는 인성 CatchPro Pro와 CatchPro Navi Pro를 함께 사용할 수 있는 통합 구독입니다.
                신청서를 남겨주시면 설치 가능 여부와 사용 방법을 순서대로 안내해드립니다.
            </p>
            <div class="catchpro-apply__actions">
                <a class="catchpro-apply__button" href="#catchpro-apply-form">신청서 작성</a>
                <?php if ($has_kakao) : ?>
                    <a class="catchpro-apply__button catchpro-apply__button--kakao" href="<?php echo esc_url($kakao_url); ?>" target="_blank" rel="noopener noreferrer">카카오톡 상담</a>
                <?php else : ?>
                    <span class="catchpro-apply__button catchpro-apply__button--disabled">카카오톡 상담 준비 중</span>
                <?php endif; ?>
                <a class="catchpro-apply__button catchpro-apply__button--ghost" href="#catchpro-apply-process">진행 방식 보기</a>
            </div>
        </section>

        <div class="catchpro-apply__wrap">
            <?php if ($is_done) : ?>
                <div class="catchpro-apply__notice">신청이 접수되었습니다. 확인 후 순서대로 연락드리겠습니다.</div>
            <?php endif; ?>

            <section aria-labelledby="catchpro-fit-title">
                <h2 id="catchpro-fit-title" class="catchpro-apply__section-title">서비스 구성</h2>
                <p class="catchpro-apply__section-copy">
                    오더를 잡는 폰과 지도를 보는 폰의 역할을 분리하면 운행 중 화면 전환이 줄어듭니다.
                    필요한 기능을 확인한 뒤 운행 방식에 맞는 구성으로 안내합니다.
                </p>
                <div class="catchpro-apply__grid">
                    <article class="catchpro-apply__panel">
                        <span class="catchpro-apply__feature-label">오더확정</span>
                        <h3>인성 CatchPro Pro</h3>
                        <p>인성앱을 사용하는 폰에서 조건 확인과 <span class="catchpro-apply__accent">오더확정</span> 동작을 빠르게 보조합니다.</p>
                    </article>
                    <article class="catchpro-apply__panel">
                        <span class="catchpro-apply__feature-label">방문순서</span>
                        <h3>CatchPro Navi Pro</h3>
                        <p><span class="catchpro-apply__accent">주소 동기화</span>, <span class="catchpro-apply__accent">방문순서</span>, 네비 실행을 지도 중심 화면으로 제공합니다.</p>
                    </article>
                    <article class="catchpro-apply__panel">
                        <span class="catchpro-apply__feature-label">설치안내</span>
                        <h3>설치 상담</h3>
                        <p>사용 기기와 운행 지역을 확인한 뒤 <span class="catchpro-apply__accent">설치 순서</span>와 이용 방법을 안내합니다.</p>
                    </article>
                </div>
            </section>

            <section id="catchpro-apply-process" aria-labelledby="catchpro-process-title">
                <h2 id="catchpro-process-title" class="catchpro-apply__section-title">진행 방식</h2>
                <div class="catchpro-apply__flow">
                    <div class="catchpro-apply__step">
                        <strong>1. 신청</strong>
                        <span>운행 지역, 사용 기기, 필요한 기능을 남깁니다.</span>
                    </div>
                    <div class="catchpro-apply__step">
                        <strong>2. 확인</strong>
                        <span>사용 기기와 필요한 기능을 확인합니다.</span>
                    </div>
                    <div class="catchpro-apply__step">
                        <strong>3. 안내</strong>
                        <span>이용 방식과 설치 절차를 안내합니다.</span>
                    </div>
                    <div class="catchpro-apply__step">
                        <strong>4. 설치</strong>
                        <span>설치 후 기본 설정을 함께 확인합니다.</span>
                    </div>
                </div>
            </section>

            <section id="catchpro-apply-form" aria-labelledby="catchpro-form-title">
                <h2 id="catchpro-form-title" class="catchpro-apply__section-title">신청서</h2>
                <p class="catchpro-apply__section-copy">
                    운행 지역, 사용 기기, 필요한 기능을 남겨주세요. 확인 후 연락드리겠습니다.
                </p>
                <div class="catchpro-apply__contact">
                    <div>
                        <strong>바로 상담이 필요하면 카카오톡으로 문의할 수 있습니다.</strong>
                        <span>신청서를 함께 남겨주시면 기기와 사용 구성을 더 빠르게 확인할 수 있습니다.</span>
                    </div>
                    <?php if ($has_kakao) : ?>
                        <a class="catchpro-apply__contact-link" href="<?php echo esc_url($kakao_url); ?>" target="_blank" rel="noopener noreferrer">카카오톡 상담 열기</a>
                    <?php else : ?>
                        <span class="catchpro-apply__contact-link catchpro-apply__contact-link--disabled">상담 링크 준비 중</span>
                    <?php endif; ?>
                </div>
                <form class="catchpro-apply__form" method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
                    <input type="hidden" name="action" value="catchpro_apply_submit">
                    <?php wp_nonce_field('catchpro_apply_submit', 'catchpro_apply_nonce'); ?>
                    <div class="catchpro-apply__hidden" aria-hidden="true">
                        <label>Website <input type="text" name="catchpro_apply_website" value=""></label>
                    </div>
                    <div class="catchpro-apply__fields">
                        <div class="catchpro-apply__field">
                            <label for="catchpro-name">이름</label>
                            <input id="catchpro-name" name="name" type="text" autocomplete="name" required>
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-phone">연락처</label>
                            <input id="catchpro-phone" name="phone" type="tel" autocomplete="tel" placeholder="010-0000-0000" required>
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-email">이메일</label>
                            <input id="catchpro-email" name="email" type="email" autocomplete="email" required>
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-device">사용 기기</label>
                            <input id="catchpro-device" name="device" type="text" placeholder="예: 갤럭시 S24, 탭, 보조폰">
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-version">희망 버전</label>
                            <input id="catchpro-version" name="version" type="text" value="CatchPro" readonly required>
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-region">주 운행 지역</label>
                            <input id="catchpro-region" name="region" type="text" placeholder="예: 수원, 용인, 오산, 이천">
                        </div>
                        <div class="catchpro-apply__field catchpro-apply__field--wide">
                            <label for="catchpro-message">문의 내용</label>
                            <textarea id="catchpro-message" name="message" placeholder="현재 사용하는 방식, 필요한 기능, 궁금한 점을 적어주세요."></textarea>
                        </div>
                    </div>
                    <label class="catchpro-apply__consent">
                        <input type="checkbox" name="privacy_consent" value="1" required>
                        <span>상담 및 설치 안내를 위해 입력한 정보를 확인하는 것에 동의합니다.</span>
                    </label>
                    <button class="catchpro-apply__submit" type="submit">신청 접수하기</button>
                    <p class="catchpro-apply__fine">접수 후 순서대로 확인해 연락드립니다.</p>
                </form>
            </section>
            <footer class="catchpro-apply__footer">
                <p><strong>CatchPro</strong> · 오더확정과 지도 네비를 더 빠르게 준비하는 운행 보조 서비스</p>
                <?php if ($has_kakao) : ?>
                    <p>상담 문의는 <a href="<?php echo esc_url($kakao_url); ?>" target="_blank" rel="noopener noreferrer">카카오톡 채널</a>에서 확인합니다.</p>
                <?php endif; ?>
                <p>© 2026 CatchPro</p>
            </footer>
        </div>
    </div>
    <script>
        (function () {
            var panels = document.querySelectorAll('.catchpro-apply__panel');
            if (!panels.length) {
                return;
            }

            if (!('IntersectionObserver' in window)) {
                panels.forEach(function (panel) {
                    panel.classList.add('is-visible');
                });
                return;
            }

            var observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    if (!entry.isIntersecting) {
                        return;
                    }
                    entry.target.classList.add('is-visible');
                    observer.unobserve(entry.target);
                });
            }, {
                rootMargin: '0px 0px -10% 0px',
                threshold: 0.2
            });

            panels.forEach(function (panel) {
                observer.observe(panel);
            });
        })();
    </script>
    <?php
    return (string) ob_get_clean();
}

function catchpro_apply_handle_submit(): void
{
    if (!isset($_POST['catchpro_apply_nonce']) || !wp_verify_nonce(sanitize_text_field(wp_unslash($_POST['catchpro_apply_nonce'])), 'catchpro_apply_submit')) {
        wp_die('신청 검증에 실패했습니다. 페이지를 새로고침한 뒤 다시 시도해주세요.', 'CatchPro 신청', ['response' => 403]);
    }

    if (!empty($_POST['catchpro_apply_website'])) {
        wp_safe_redirect(home_url('/catchpro-pro-apply/'));
        exit;
    }

    if (empty($_POST['privacy_consent'])) {
        wp_die('개인정보 수집 동의가 필요합니다.', 'CatchPro 신청', ['response' => 400]);
    }

    $name = catchpro_apply_field('name');
    $phone = catchpro_apply_field('phone');
    $email = sanitize_email(catchpro_apply_field('email'));
    $device = catchpro_apply_field('device');
    $version = catchpro_apply_field('version');
    $region = catchpro_apply_field('region');
    $message = catchpro_apply_textarea('message');

    if ($name === '' || $phone === '' || $email === '' || $version === '') {
        wp_die('필수 항목을 입력해주세요.', 'CatchPro 신청', ['response' => 400]);
    }

    $summary = [
        '이름: ' . $name,
        '연락처: ' . $phone,
        '이메일: ' . $email,
        '사용 기기: ' . ($device !== '' ? $device : '-'),
        '희망 버전: ' . $version,
        '주 운행 지역: ' . ($region !== '' ? $region : '-'),
        '',
        '문의 내용:',
        $message !== '' ? $message : '-',
    ];

    $post_id = wp_insert_post([
        'post_type' => 'catchpro_application',
        'post_status' => 'private',
        'post_title' => sprintf('[%s] %s / %s', wp_date('Y-m-d H:i'), $name, $version),
        'post_content' => implode("\n", $summary),
    ], true);

    if (is_wp_error($post_id)) {
        wp_die('신청 저장에 실패했습니다. 잠시 후 다시 시도해주세요.', 'CatchPro 신청', ['response' => 500]);
    }

    update_post_meta($post_id, 'catchpro_name', $name);
    update_post_meta($post_id, 'catchpro_phone', $phone);
    update_post_meta($post_id, 'catchpro_email', $email);
    update_post_meta($post_id, 'catchpro_device', $device);
    update_post_meta($post_id, 'catchpro_version', $version);
    update_post_meta($post_id, 'catchpro_region', $region);
    update_post_meta($post_id, 'catchpro_submitted_ip_hash', wp_hash($_SERVER['REMOTE_ADDR'] ?? ''));

    $target = wp_get_referer();
    if (!$target) {
        $target = home_url('/catchpro-pro-apply/');
    }

    wp_safe_redirect(add_query_arg('catchpro_apply', 'success', $target) . '#catchpro-apply-form');
    exit;
}

function catchpro_apply_handle_license_upsert(): void
{
    if (!current_user_can('manage_options')) {
        wp_die('권한이 없습니다.', 'CatchPro 라이선스', ['response' => 403]);
    }
    if (!isset($_POST['catchpro_license_nonce']) || !wp_verify_nonce(sanitize_text_field(wp_unslash($_POST['catchpro_license_nonce'])), 'catchpro_license_upsert')) {
        wp_die('라이선스 요청 검증에 실패했습니다.', 'CatchPro 라이선스', ['response' => 403]);
    }

    $license_action = catchpro_apply_field('license_action');
    $payload = [
        'name' => catchpro_apply_field('name'),
        'phone' => catchpro_apply_field('phone'),
        'email' => sanitize_email(catchpro_apply_field('email')),
        'edition' => catchpro_apply_license_product_code(),
        'status' => catchpro_apply_field('status') ?: 'trial',
        'memo' => catchpro_apply_field('memo'),
        'allowDeviceBind' => true,
        'maxDevices' => 2,
    ];
    $notice = 'saved';

    if ($license_action === 'save') {
        $extend_days = (int) catchpro_apply_field('extend_days');
        if ($extend_days > 0) {
            $payload['extendDays'] = $extend_days;
        }
    } elseif ($license_action === 'extend_30') {
        $payload['status'] = 'active';
        $payload['extendDays'] = 30;
        $notice = 'extended';
    } elseif ($license_action === 'trial_30') {
        $payload['status'] = 'trial';
        $payload['extendDays'] = 30;
        $notice = 'extended';
    } elseif ($license_action === 'reset_device') {
        $payload['resetDevice'] = true;
        $notice = 'device_reset';
    } elseif ($license_action === 'expire') {
        $payload['status'] = 'expired';
        $payload['expiresAt'] = gmdate('c');
        $notice = 'expired';
    } elseif ($license_action === 'block') {
        $payload['status'] = 'blocked';
        $notice = 'blocked';
    }

    $result = catchpro_apply_license_request('POST', '/api/license/upsert', $payload);
    if (is_wp_error($result)) {
        wp_die(esc_html($result->get_error_message()), 'CatchPro 라이선스', ['response' => 500]);
    }

    wp_safe_redirect(add_query_arg('catchpro_license_notice', $notice, admin_url('edit.php?post_type=catchpro_application&page=catchpro-license-manager')));
    exit;
}

function catchpro_apply_handle_license_from_application(): void
{
    if (!current_user_can('manage_options')) {
        wp_die('권한이 없습니다.', 'CatchPro 라이선스', ['response' => 403]);
    }
    if (!isset($_POST['catchpro_license_nonce']) || !wp_verify_nonce(sanitize_text_field(wp_unslash($_POST['catchpro_license_nonce'])), 'catchpro_license_from_application')) {
        wp_die('라이선스 요청 검증에 실패했습니다.', 'CatchPro 라이선스', ['response' => 403]);
    }

    $post_id = (int) catchpro_apply_field('post_id');
    if ($post_id <= 0 || get_post_type($post_id) !== 'catchpro_application') {
        wp_die('신청 정보를 찾지 못했습니다.', 'CatchPro 라이선스', ['response' => 404]);
    }

    $payload = [
        'name' => (string) get_post_meta($post_id, 'catchpro_name', true),
        'phone' => (string) get_post_meta($post_id, 'catchpro_phone', true),
        'email' => sanitize_email((string) get_post_meta($post_id, 'catchpro_email', true)),
        'edition' => catchpro_apply_license_product_code(),
        'status' => 'trial',
        'extendDays' => 30,
        'allowDeviceBind' => true,
        'maxDevices' => 2,
        'memo' => '신청서에서 통합 Pro 1개월 무료체험 등록',
    ];
    $result = catchpro_apply_license_request('POST', '/api/license/upsert', $payload);
    if (is_wp_error($result)) {
        wp_die(esc_html($result->get_error_message()), 'CatchPro 라이선스', ['response' => 500]);
    }

    update_post_meta($post_id, 'catchpro_license_registered_at', current_time('mysql'));
    update_post_meta($post_id, 'catchpro_license_registered_editions', catchpro_apply_license_product_code());

    wp_safe_redirect(add_query_arg('catchpro_license_notice', 'saved', get_edit_post_link($post_id, 'raw')));
    exit;
}
