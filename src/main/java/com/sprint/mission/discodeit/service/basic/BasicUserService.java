package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.data.UserDto;
import com.sprint.mission.discodeit.dto.request.BinaryContentCreateRequest;
import com.sprint.mission.discodeit.dto.request.UserCreateRequest;
import com.sprint.mission.discodeit.dto.request.UserUpdateRequest;
import com.sprint.mission.discodeit.entity.BinaryContent;
import com.sprint.mission.discodeit.entity.BinaryContentUploadStatus;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.exception.user.UserAlreadyExistsException;
import com.sprint.mission.discodeit.exception.user.UserNotFoundException;
import com.sprint.mission.discodeit.mapper.UserMapper;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.security.jwt.JwtService;
import com.sprint.mission.discodeit.security.jwt.JwtSession;
import com.sprint.mission.discodeit.service.UserService;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

@Slf4j
@RequiredArgsConstructor
@Service
public class BasicUserService implements UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final BinaryContentRepository binaryContentRepository;
  private final BinaryContentStorage binaryContentStorage;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  @Transactional
  @CacheEvict(value = "users", key = "'all'")
  @Override
  public UserDto create(UserCreateRequest userCreateRequest,
      Optional<BinaryContentCreateRequest> optionalProfileCreateRequest) {
    log.debug("사용자 생성 시작: {}", userCreateRequest);

    String username = userCreateRequest.username();
    String email = userCreateRequest.email();

    if (userRepository.existsByEmail(email)) {
      throw UserAlreadyExistsException.withEmail(email);
    }
    if (userRepository.existsByUsername(username)) {
      throw UserAlreadyExistsException.withUsername(username);
    }

    BinaryContent nullableProfile = optionalProfileCreateRequest
        .map(profileRequest -> {
          String fileName = profileRequest.fileName();
          String contentType = profileRequest.contentType();
          byte[] bytes = profileRequest.bytes();
          BinaryContent binaryContent = new BinaryContent(fileName, (long) bytes.length,
              contentType);
          binaryContentRepository.save(binaryContent);
          TransactionSynchronizationManager.registerSynchronization(
              new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                  binaryContentStorage.putAsync(binaryContent.getId(), bytes)
                      .thenAccept(result -> {
                        log.debug("프로필 이미지 업로드 성공: {}", binaryContent.getId());
                        binaryContentRepository.updateUploadStatus(binaryContent.getId(),
                            BinaryContentUploadStatus.SUCCESS);
                      })
                      .exceptionally(throwable -> {
                        log.error("프로필 이미지 업로드 실패: {}", throwable.getMessage());
                        binaryContentRepository.updateUploadStatus(binaryContent.getId(),
                            BinaryContentUploadStatus.FAILED);
                        return null;
                      })
                  ;
                }
              });

          return binaryContent;
        })
        .orElse(null);
    String password = userCreateRequest.password();

    String hashedPassword = passwordEncoder.encode(password);
    User user = new User(username, email, hashedPassword, nullableProfile);

    userRepository.save(user);
    log.info("사용자 생성 완료: id={}, username={}", user.getId(), username);
    return userMapper.toDto(user);
  }

  @Transactional(readOnly = true)
  @Override
  public UserDto find(UUID userId) {
    log.debug("사용자 조회 시작: id={}", userId);
    UserDto userDto = userRepository.findById(userId)
        .map(userMapper::toDto)
        .orElseThrow(() -> UserNotFoundException.withId(userId));
    log.info("사용자 조회 완료: id={}", userId);
    return userDto;
  }

  @Cacheable(value = "users", key = "'all'", unless = "#result.isEmpty()")
  @Override
  public List<UserDto> findAll() {
    log.debug("모든 사용자 조회 시작");
    Set<UUID> onlineUserIds = jwtService.getActiveJwtSessions().stream()
        .map(JwtSession::getUserId)
        .collect(Collectors.toSet());

    List<UserDto> userDtos = userRepository.findAllWithProfile()
        .stream()
        .map(user -> userMapper.toDto(user, onlineUserIds.contains(user.getId())))
        .toList();
    log.info("모든 사용자 조회 완료: 총 {}명", userDtos.size());
    return userDtos;
  }

  @PreAuthorize("hasRole('ADMIN') or principal.userDto.id == #userId")
  @Transactional
  @CacheEvict(value = "users", key = "'all'")
  @Override
  public UserDto update(UUID userId, UserUpdateRequest userUpdateRequest,
      Optional<BinaryContentCreateRequest> optionalProfileCreateRequest) {
    log.debug("사용자 수정 시작: id={}, request={}", userId, userUpdateRequest);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          UserNotFoundException exception = UserNotFoundException.withId(userId);
          return exception;
        });

    String newUsername = userUpdateRequest.newUsername();
    String newEmail = userUpdateRequest.newEmail();

    if (userRepository.existsByEmail(newEmail)) {
      throw UserAlreadyExistsException.withEmail(newEmail);
    }

    if (userRepository.existsByUsername(newUsername)) {
      throw UserAlreadyExistsException.withUsername(newUsername);
    }

    BinaryContent nullableProfile = optionalProfileCreateRequest
        .map(profileRequest -> {
          String fileName = profileRequest.fileName();
          String contentType = profileRequest.contentType();
          byte[] bytes = profileRequest.bytes();
          BinaryContent binaryContent = new BinaryContent(fileName, (long) bytes.length,
              contentType);
          binaryContentRepository.save(binaryContent);
          TransactionSynchronizationManager.registerSynchronization(
              new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                  binaryContentStorage.putAsync(binaryContent.getId(), bytes)
                      .thenAccept(result -> {
                        log.debug("프로필 이미지 업로드 성공: {}", binaryContent.getId());
                        binaryContentRepository.updateUploadStatus(binaryContent.getId(),
                            BinaryContentUploadStatus.SUCCESS);
                      })
                      .exceptionally(throwable -> {
                        log.error("프로필 이미지 업로드 실패: {}", throwable.getMessage());
                        binaryContentRepository.updateUploadStatus(binaryContent.getId(),
                            BinaryContentUploadStatus.FAILED);
                        return null;
                      })
                  ;
                }
              });

          return binaryContent;
        })
        .orElse(null);

    String newPassword = userUpdateRequest.newPassword();
    String hashedNewPassword = Optional.ofNullable(newPassword).map(passwordEncoder::encode)
        .orElse(null);
    user.update(newUsername, newEmail, hashedNewPassword, nullableProfile);

    log.info("사용자 수정 완료: id={}", userId);
    return userMapper.toDto(user);
  }

  @PreAuthorize("hasRole('ADMIN') or principal.userDto.id == #userId")
  @Transactional
  @CacheEvict(value = "users", key = "'all'")
  @Override
  public void delete(UUID userId) {
    log.debug("사용자 삭제 시작: id={}", userId);

    if (!userRepository.existsById(userId)) {
      throw UserNotFoundException.withId(userId);
    }

    userRepository.deleteById(userId);
    log.info("사용자 삭제 완료: id={}", userId);
  }
}
